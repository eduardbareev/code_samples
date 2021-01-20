#!/usr/bin/env python

import argparse
import glob
import html
import os
import re
import shutil
import subprocess
import traceback
import urllib
import urllib.parse
from copy import deepcopy

import lxml.etree as ET
import lxml.objectify

import common
import conf

verbose = False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dbk_in", type=argparse_check_in_path)
    parser.add_argument("--verbose", action='store_true')
    parser.add_argument("--always-rebuild", action='store_true')
    out_path_args_helper = common.OutPathArgs(parser)
    args = parser.parse_args()
    out_dirs = out_path_args_helper.get_out_dirs(args)

    global verbose
    verbose = args.verbose
    build(args.dbk_in, out_dirs, args.always_rebuild)


def halt(msg):
    common.log_stderr(msg)
    raise Exception(msg)


def print_verbose(msg):
    if not verbose:
        return
    common.log_stderr(msg)


def check_need_rebuild(dbk_in_file, out_path):
    REBUILD = common.SHOULD_REBUILD
    rebuild = common.mtime_should_rebuild(dbk_in_file, out_path)
    if rebuild in (REBUILD.YES_STALE,
                   REBUILD.YES_WAS_NEVER_BUILT):
        print_verbose("needs update " + out_path)
        return True
    elif rebuild == REBUILD.NO_PRODUCT_IS_NEWER:
        halt(f"refusing to overwrite {out_path}. "
             f"it's is newer than {dbk_in_file}")
    elif rebuild == REBUILD.NO_UP_TO_DATE:
        print_verbose("skip " + out_path)
        return False
    else:
        raise Exception("can't happen")
    return False


def build(dbk_in_file_path, out_dirs,
          always_rebuild=False, out_file_name=None,
          limit_to_profiles=None):
    out_paths = {p_name: output_file_full_path(
        dbk_in_file_path, out_dirs[p_name], out_file_name)
        for p_name in conf.profiles.keys()}

    need_rebuild = check_need_rebuild(dbk_in_file_path, out_paths['internal'][0])
    if (not always_rebuild) and (not need_rebuild):
        return out_paths

    # first checking well-formedness by loading it with libxml
    # and keeping parsed tree.
    well_formed, xml, err_msg = check_valid_xml(dbk_in_file_path)

    if not well_formed:
        common.log_stderr("xml error: " + err_msg)
        for p, _ in out_paths.values():
            dump_err_as_product(err_msg, p)
            halt(err_msg)

    # now validating same file against schema.
    # jing can't validate stdin
    valid, err_msg = validate_docbook_jing(dbk_in_file_path)
    if not valid:
        common.log_stderr("docbook authoring error: " + err_msg)
        for p, _ in out_paths.values():
            dump_err_as_product(err_msg, p)
        return

    indoc_proc_params = extract_proc_params(xml)
    input_doc_xml = process_status_attr(xml)
    input_doc_xml = resolve_assets_paths(input_doc_xml, dbk_in_file_path)

    doc_produced_by_profile = {}

    profiles = conf.profiles
    if limit_to_profiles is not None:
        profiles = {k:profiles[k] for k in profiles if k in limit_to_profiles}

    try:
        for profile_name, props in profiles.items():
            p_input_doc_xml = deepcopy(input_doc_xml)
            profile_sett_str, produce_test, ext_init_params, add_src_cmd_flag = props

            out_dir = out_dirs[profile_name]
            out_file_path, web_rel_path = out_paths[profile_name]

            if not produce_test(p_input_doc_xml):
                # make sure no previous versions of build product exist
                # if no public sections left in source document
                del_if_exists(out_file_path)
                continue

            if add_src_cmd_flag:
                p_input_doc_xml = add_src_cmd(p_input_doc_xml, dbk_in_file_path)

            transform_result = xslt_transform_with_profile(
                p_input_doc_xml, profile_sett_str, ext_init_params, indoc_proc_params)

            of = open(out_file_path, 'w')
            of.write(transform_result)
            of.flush()
            of.close()

            common.transfer_mtime(dbk_in_file_path, out_file_path)
            copy_shared_web_assets(out_dir)
            copy_document_web_assets(dbk_in_file_path, out_file_path)
            common.log_stderr("written: " + out_file_path)
            doc_produced_by_profile[profile_name] = (out_file_path, web_rel_path)
        return doc_produced_by_profile

    except Exception as e:
        err_msg = "xslt transformation error: " + traceback.format_exc()
        common.log_stderr(err_msg)
        for p, _ in out_paths.values():
            dump_err_as_product(err_msg, p)
        raise e


def add_src_cmd(xml, dbk_in_file_path):
    def make_cmd(in_path):
        absp = os.path.abspath(in_path)
        relp = os.path.relpath(absp, start=conf.SRC_DOC_TAG['make_rel_to'])
        cmd = conf.SRC_DOC_TAG['cmd_format'].format(path=relp)
        return cmd

    root_section_els = xml.xpath("/db:article/db:section[position()=1]",
                                 namespaces=conf.LXML_XPATH_NSMAP)
    doc_source = ET.Element("screen")
    doc_source.text = make_cmd(dbk_in_file_path)
    if len(root_section_els):
        root_section_els[0].addprevious(doc_source)
    else:
        article = xml.xpath("/db:article", namespaces=conf.LXML_XPATH_NSMAP)[0]
        article.insert(0, doc_source)
    return xml


def extract_proc_params(xml):
    pse = xml.xpath('/*/db:procparams', namespaces=conf.LXML_XPATH_NSMAP)
    if len(pse):
        ps: ET._Element = pse[0]
        ps = lxml.objectify.XML(ET.tostring(ps))
        return ps


def get_out_doc_assets_subdir_rel(in_doc_file_name):
    in_doc_file_basename = os.path.splitext(os.path.split(in_doc_file_name)[1])[0]
    return f"{in_doc_file_basename}-doc-assets"


def copy_document_web_assets(dbk_in_file, out_file_path):
    in_doc_assets_parent_dir = os.path.join(conf.SCRIPT_DIR, conf.DOC_ASSETS_DIR, )
    in_doc_dir, in_doc_file_name = os.path.split(dbk_in_file)
    in_doc_file_basename = os.path.splitext(in_doc_file_name)[0]
    in_doc_assets_subdir = os.path.join(in_doc_assets_parent_dir, in_doc_file_basename)

    out_doc_dir, out_doc_file_basename = os.path.split(out_file_path)
    out_doc_assets_subdir_rel = get_out_doc_assets_subdir_rel(in_doc_file_name)
    out_doc_assets_subdir_abs = os.path.join(out_doc_dir, out_doc_assets_subdir_rel)

    g_expr_base = os.path.join(in_doc_assets_subdir, "**/*")
    g_expr_exts = ('.png', '.webm', '.svg')
    files_to_copy_abs = [path for e in g_expr_exts
                         for path in glob.glob(g_expr_base + e, recursive=True)]
    files_to_copy_rel = [os.path.relpath(f, in_doc_assets_subdir) for f in files_to_copy_abs]

    if len(files_to_copy_rel):
        os.makedirs(out_doc_assets_subdir_abs, exist_ok=True)
        sync_dir(in_doc_assets_subdir, files_to_copy_rel, out_doc_assets_subdir_abs)


def copy_shared_web_assets(out_dir):
    out_dir_assets = os.path.join(out_dir, conf.WEB_ASSETS_DIR_NAME_DST)
    os.makedirs(out_dir_assets, exist_ok=True)

    src_dir = os.path.join(conf.SCRIPT_DIR, conf.WEB_ASSETS_DIR_NAME_SRC)

    g_expr_base = os.path.join(src_dir, "**/*")
    g_expr_exts = ('.css', '.woff2')

    files_to_copy_abs = [path for e in g_expr_exts
                         for path in glob.glob(g_expr_base + e, recursive=True)]
    files_to_copy_rel = [os.path.relpath(f, src_dir) for f in files_to_copy_abs]

    sync_dir(src_dir, files_to_copy_rel, out_dir_assets)


def sync_dir(src_dir, src_files_rel_paths, dst_dir):
    SHOULD_REBUILD = common.SHOULD_REBUILD
    for src_file_rel in src_files_rel_paths:
        src_file_abs = os.path.join(src_dir, src_file_rel)
        dst_file_abs = os.path.join(dst_dir, src_file_rel)

        should_rebuild = common.mtime_should_rebuild(src_file_abs, dst_file_abs)
        dst_subdir = os.path.dirname(dst_file_abs)
        os.makedirs(dst_subdir, exist_ok=True)

        if should_rebuild in (SHOULD_REBUILD.YES_STALE,
                              SHOULD_REBUILD.YES_WAS_NEVER_BUILT):
            common.log_stderr("update " + dst_file_abs)
            shutil.copy(src_file_abs, dst_file_abs)
            common.transfer_mtime(src_file_abs, dst_file_abs)
        elif should_rebuild == SHOULD_REBUILD.NO_PRODUCT_IS_NEWER:
            common.log_stderr(f"refusing to overwrite {dst_file_abs}. "
                              f"it's is newer than {src_file_abs}")
        elif should_rebuild == SHOULD_REBUILD.NO_UP_TO_DATE:
            print_verbose("skip " + dst_file_abs)
        else:
            raise Exception("can't happen")


def output_file_full_path(docbook_in_path, out_dir, out_file_name):
    if out_file_name is None:
        _, in_file_name = os.path.split(docbook_in_path)
        in_basename, _ = os.path.splitext(in_file_name)
    else:
        in_basename = out_file_name
    web_rel_path = f"{in_basename}.html"
    out_path = os.path.join(out_dir, web_rel_path)
    return out_path, web_rel_path


def dump_err_as_product(error_message, out_file):
    dump = """<html><title>error</title>
                <body><pre style="white-space: pre-wrap;">{}
                    </pre></body></html>""" \
        .format(html.escape(error_message))
    f = open(out_file, "w")
    f.write(dump)
    f.flush()
    f.close()
    common.mtime(out_file, 0)


def check_valid_xml(file_path):
    try:
        return True, ET.parse(file_path), None
    except Exception as e:
        return False, None, str(e)


def xslt_transform_with_profile(dbk_in, profile,
                                ext_init_params, indoc_proc_params):
    xslt_doc = ET.parse(conf.XSLT_PATH)

    add_xslt_params = {"profile.status": ET.XSLT.strparam(profile)}

    if hasattr(indoc_proc_params, 'incl_toc'):
        incl_toc = bool(int(indoc_proc_params.incl_toc))
    else:
        incl_toc = True

    if not incl_toc:
        add_xslt_params["generate.toc"] = ET.XSLT.strparam('article nop')
        pass

    trans_result = run_transform(
        dbk_in, xslt_doc, add_xslt_params, ext_init_params)

    return str(trans_result)


def replace_rel_doc_links(uri, select_nth_mirror):
    rel_uri_p = urllib.parse.urlparse(uri)
    if rel_uri_p.scheme != 'reldoc':
        return uri

    loc_mirrors = conf.subs_mul_loc[rel_uri_p.netloc]
    if len(loc_mirrors) <= select_nth_mirror:
        select_nth_mirror = 0
    subs_tpl = loc_mirrors[select_nth_mirror]
    subs_tpl_parts = urllib.parse.urlparse(subs_tpl)

    rel_uri_p = rel_uri_p._replace(scheme=subs_tpl_parts.scheme)
    rel_uri_p = rel_uri_p._replace(netloc=subs_tpl_parts.netloc)
    new_path = urllib.parse.urljoin(subs_tpl_parts.path, rel_uri_p.path.lstrip('/'))
    rel_uri_p = rel_uri_p._replace(path=new_path)

    return urllib.parse.urlunparse(rel_uri_p)


def man_page_link(page, section, mirror):
    if len(section) > 1:
        s_num = section[0]
        s_ext = section[1]
    else:
        s_num = section
        s_ext = ''

    tpl = conf.subs_mul_loc['MAN_PAGES'][mirror]

    url = tpl.format(s_num=s_num, s_ext=s_ext, article=page)
    anchor = f"man {section} {page}"

    return url, anchor


def uninent_block_literal_el(el: ET._Element):
    if (0 == len(el.getchildren())) and (el.text is None):
        return el

    def get_inner_content_xml(el_):
        s = ET.tostring(el_, encoding="unicode", xml_declaration=False)
        # lxml provides no way to extract literal XML of node content
        # excluding the node itself
        m = re.match('([^>]*>)(.+)(<[^>]+>)\\s*$', s, re.DOTALL)
        return m.groups()

    start, contents, end = get_inner_content_xml(el)

    lines = rm_lead_trail_empty_lines(contents)
    lines = unindent(lines)
    xml_s = start + "\n".join(lines) + end
    xml = ET.XML(xml_s)

    return xml


def rows_cols_el(el: ET._Element):
    recursive_text = ET.tostring(el, method="text", encoding="unicode")
    return rows_cols(recursive_text)


def rows_cols(text):
    lines = rm_lead_trail_empty_lines(text)
    lines = unindent(lines)
    if not len(lines):
        return 0, 0
    return len(lines), max([len(ln) for ln in lines])


class PyXSLTExtResolveDocLink(ET.XSLTExtension):
    def __init__(self, ext_init_params):
        super().__init__()
        self.select_nth_doc_mirror = ext_init_params[0]

    def execute(self, ctx, self_node, input_node, output_parent):
        uri = input_node.get(conf.LXML_XLINK_PREFIX + 'href')
        resolved_uri = replace_rel_doc_links(uri, self.select_nth_doc_mirror)
        ret_node = ET.Element("return-node")
        ret_node.text = resolved_uri
        output_parent.append(ret_node)


class PyXSLTExtManPage(ET.XSLTExtension):
    def __init__(self, ext_init_params):
        super().__init__()
        self.select_nth_doc_mirror = ext_init_params[0]

    def execute(self, ctx, self_node, input_node, output_parent):
        ret_node: ET._Element = ET.Element(conf.LXML_DB_PREFIX + "link")
        url, anchor = man_page_link(
            input_node.get('article'),
            input_node.get('section'),
            self.select_nth_doc_mirror
        )
        ret_node.set(conf.LXML_XLINK_PREFIX + 'href', url)
        ret_node.text = anchor
        output_parent.append(ret_node)


class PyXSLTExtUnindentBlockLiterals(ET.XSLTExtension):
    def execute(self, ctx, self_node, input_node, output_parent):
        programlisting_copy = deepcopy(input_node)
        programlisting_copy.set('no-recursion', str(1))
        programlisting_copy = uninent_block_literal_el(programlisting_copy)
        output_parent.append(programlisting_copy)


class PyXSLTExtLiteralBlockWidth(ET.XSLTExtension):
    def execute(self, ctx, self_node, input_node, output_parent):
        literal_els = ('screen', 'programlisting')
        # xpath is not available for ReadOnlyElementProxy
        literal_node = None
        if ET.QName(input_node.tag).localname in literal_els:
            literal_node = input_node
        else:
            for elem in input_node.getchildren():
                if callable(elem.tag):
                    # <class 'cython_function_or_method'> for xml comments
                    continue
                try:
                    localname = ET.QName(elem.tag).localname
                except Exception as excp:
                    raise Exception(
                        f"can't get local name for {elem.tag} {elem.tag.__class__.__name__}")

                if localname in literal_els:
                    literal_node = elem
                    break

            if literal_node is None:
                raise Exception("can't find literal_node")
        literal_node = deepcopy(literal_node)
        rows, cols = rows_cols_el(literal_node)
        L1 = 75
        L2 = 92
        if cols <= L1:
            cols_g = 'l1'
        elif cols <= L2:
            cols_g = 'l2'
        else:
            cols_g = 'l3'

        size = ET.Element("size")
        size.set("cols_g", cols_g)
        size.set("cols_n", str(cols))
        output_parent.append(size)


def init_xslt_extension(ext_init_params):
    extensions = {
        ('pyextnsuri', 'resolve-doc-link'): PyXSLTExtResolveDocLink(ext_init_params),
        ('pyextnsuri', 'make-man-page-link'): PyXSLTExtManPage(ext_init_params),
        ('pyextnsuri', 'unindent-programlisting'): PyXSLTExtUnindentBlockLiterals(),
        ('pyextnsuri', 'line-width'): PyXSLTExtLiteralBlockWidth(),
    }
    return extensions


def run_transform(inp_doc, xslt_doc, add_xslt_params, ext_init_params):
    extensions = init_xslt_extension(ext_init_params)
    transformation = ET.XSLT(xslt_doc, extensions=extensions)
    handle_xslt_error_log(transformation.error_log)

    run_params = {**conf.xslt_base_params_kw, **add_xslt_params}

    result = transformation(inp_doc, **run_params)
    handle_xslt_error_log(transformation.error_log)

    return result


def handle_xslt_error_log(log: ET._ListErrorLog):
    log = [e.message for e in log.filter_from_warnings()]
    if not len(log):
        return

    false_dbk_xslt_errs = (
        'Writing docbook.css for article',
        'added namespace',
        'processing stripped document',
    )

    log = list(filter(lambda entry: not any(map(entry.__contains__, false_dbk_xslt_errs)), log))

    if len(log):
        raise Exception("xslt transformation errors "
                        "occurred: " + ";".join(log))


def resolve_asset_path(uri, web_basedir):
    u = urllib.parse.urlunparse(urllib.parse.urlparse(uri)._replace(scheme=''))
    u = re.sub('^/*', '', u)
    u = f"{web_basedir}/{u}"
    return u


def resolve_assets_paths(doc, dbk_in_file_path):
    doc: ET._Element
    doc = doc.xpath('/*')[0]
    attr_name = 'fileref'
    scheme = 'docassets'
    xpathexpr = f"//db:imagedata[starts-with(@{attr_name}, '{scheme}')]|" \
                f"//db:videodata[starts-with(@{attr_name}, '{scheme}')]"
    images = doc.xpath(xpathexpr,
                       namespaces=conf.LXML_XPATH_NSMAP)
    base_path = get_out_doc_assets_subdir_rel(dbk_in_file_path)

    i: ET._Element
    for i in images:
        uri = i.get(attr_name)
        i.set(attr_name, resolve_asset_path(uri, base_path))
    return doc


def process_status_attr(doc):
    doc = doc.xpath("/*")[0]

    # find all elements with public status. starting
    # from each element, climb through all ancestors
    # up to the top element, and set their status to "mixed"
    ancestors_of_public_els \
        = doc.xpath("//*[descendant::*[@status='public']]")
    for a in ancestors_of_public_els:
        a.set("status", "mixed")

    # we include title even for partially included elements to
    # preserve document integrity. e.g. I can mark
    # example/programlisting as public but forget to mark its
    # example/title so. section/title must be public even if
    # section just partially included into document. the same is
    # also the case for document as whole: article needs
    # article/title to be valid.
    r = doc.xpath(
        """//*[@status='mixed' ]/
            child::*[name() = 'title' or name() = 'info']
        """,
        namespaces=conf.LXML_XPATH_NSMAP)
    for e in r:
        e.set('status', 'public')

    # find all elements with status mixed or public.
    # for each element collect their surrounding.
    # include only siblings with no status set.
    # set their status to internal
    r = doc.xpath(
        """//*[@status='mixed' or @status='public']/
                preceding-sibling::*[not(@status)]
          |//*[@status='mixed' or @status='public']/
                following-sibling::*[not(@status)]""",
        namespaces=conf.LXML_XPATH_NSMAP)
    for e in r:
        e.set('status', 'internal')

    # select public and mixed nodes holding no status="internal" elements
    r = doc.xpath("""
                //*[(@status='mixed') and 
                (0 = count(descendant::*[@status='internal']))]
                """,
                  namespaces=conf.LXML_XPATH_NSMAP)
    for e in r:
        # make them "public"
        e.set("status", "public")
        r2 = e.xpath("descendant::*[@status]")
        for e2 in r2:
            del e2.attrib['status']

    # only keep status attribute for highest public node
    r = doc.xpath("""
                //*[(@status='public')]/descendant::*[@status='public']
                """,
                  namespaces=conf.LXML_XPATH_NSMAP)
    for e in r:
        del e.attrib['status']

    # ensuring constraints are met.
    # there is should be no [P|I]//[M|P|I].
    r = doc.xpath("""
        count(//*[@status='internal' or @status='[public]']
                /descendant::*[@status])""",
                  namespaces=conf.LXML_XPATH_NSMAP)
    assert (int(r) == 0)

    r = doc.xpath("""//*[@status='mixed']""",
                  namespaces=conf.LXML_XPATH_NSMAP)
    for e in r:
        del e.attrib['status']

    return doc


def descend(el: ET._Element, set_val):
    put_attr(el, set_val)
    for d in el.iterdescendants():
        put_attr(d, set_val)


def climb(el: ET._Element, set_val):
    p = el
    put_attr(p, set_val)
    while (p := p.getparent()) is not None:
        put_attr(p, set_val)


def put_attr(e: ET._Element, set_val):
    if e.get("status") is None:
        e.set("status", set_val)


def argparse_check_in_path(path):
    if not os.path.isfile(path):
        raise argparse.ArgumentTypeError(f"{path} is not file")
    return path


def validate_docbook_jing(file_path):
    opts = ["jing", "-c", conf.SCHEMA_PATH, file_path]
    try:
        # jing uses stdout for errors
        subprocess.run(opts, check=True, stdout=subprocess.PIPE)
        return True, None
    except subprocess.CalledProcessError as e:
        return False, e.stdout.decode('utf8')


def del_if_exists(path):
    if os.path.exists(path):
        os.remove(path)


def find_curr_indent(lines):
    min_indent = None
    for line in lines:
        if re.match("^\\s*$", line, ):
            continue
        m = re.match("(\\s*)(?=\\S+)", line)
        indent = len(m.group(1))
        if min_indent is None:
            min_indent = indent
        if indent < min_indent:
            min_indent = indent
    return min_indent or 0


def unindent(lines):
    min_indent = find_curr_indent(lines)
    return [ln[min_indent:] for ln in lines]


def rm_lead_trail_empty_lines(text):
    if text is None:
        return []
    lines = text.splitlines()
    if len(lines) == 0:
        return []

    lines = ["" if re.match("^\\s*$", ln) else ln for ln in lines]
    first_nempty = None
    last_nempty = None
    for (n, l) in enumerate(lines):
        if not re.match("^\\s*$", l):
            if first_nempty is None:
                first_nempty = n
            last_nempty = n
    if first_nempty is None:
        return []
    return lines[first_nempty:last_nempty + 1]


if __name__ == "__main__":
    main()
