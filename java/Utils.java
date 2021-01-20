package com.drscbt.shared.utils;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static long copy(InputStream input, OutputStream output) {
        byte[] buffer = new byte[512];
        long count = 0;
        int n;
        try {
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                count += n;
            }
            output.flush();
            output.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return count;
    }

    public static byte[] streamToByteArr(InputStream inpStream) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(inpStream, baos);
        return baos.toByteArray();
    }

    public static int[] streamToIntArr(InputStream inpStream) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(inpStream, baos);
        return byteArrToIntArrPack(baos.toByteArray());
    }

    public static String streamToString(InputStream inpStream, String encodingName) {
        try {
            InputStreamReader inpStreamReader = new InputStreamReader(inpStream, encodingName);
            return streamReaderToString(inpStreamReader);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String streamReaderToString(InputStreamReader inpStreamReader) {
        StringBuffer sb = new StringBuffer();
        char[] buff = new char[512];
        int ret = 0;
        try {
            while (-1 != (ret = inpStreamReader.read(buff, 0, buff.length))) {
                sb.append(buff, 0, ret);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    static public int max(int a, int b, int c) {
        if (a > b) {
            if (a > c) {
                return a;
            } else {
                return c;
            }
        } else {
            if (b > c) {
                return b;
            } else {
                return c;
            }
        }
    }

    static public int min(int a, int b, int c) {
        if (a < b) {
            if (a < c) {
                return a;
            } else {
                return c;
            }
        } else {
            if (b < c) {
                return b;
            } else {
                return c;
            }
        }
    }

    static public int toUnsignedInt(byte b){
        return b & 0xFF;
    }

    static public void dumpToFile(InputStream is, String filename) {
        try {
            FileOutputStream fos = new FileOutputStream(filename);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            copy(is, bos);
            bos.flush();
            bos.close();
            fos.flush();
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static public void dumpToFile(byte[] byteArr, String filename) {
        try {
            FileOutputStream fos = new FileOutputStream(filename);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            bos.write(byteArr);
            bos.flush();
            bos.close();
            fos.flush();
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static public byte[] md5(byte[] arr) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md.update(arr);
        return md.digest();
    }

    static public byte[] md5(int[] arr) {
        int al = arr.length;
        byte[] bs = new byte[al * 4];
        for (int i = 0; i < al; i++) {
            for (int j = 3; j >= 0; j--) {
                int pos = (i * 4) + (3 - j);
                bs[pos] = (byte)((arr[i] >>> (8 * j)) & 0xFF);;
            }
        }

        MessageDigest mdInts;
        try {
            mdInts = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        mdInts.update(bs);
        return mdInts.digest();
    }

    public static byte[] toBytes(int num) {
        //BE
        return new byte[] {
            (byte)((num >> (8 * 3)) & 0xFF),
            (byte)((num >> (8 * 2)) & 0xFF),
            (byte)((num >> (8)) & 0xFF),
            (byte)(num & 0xFF)
        };
    }

    public static int fourBytesToInt(byte b0, byte b1, byte b2, byte b3) {
        return (toUnsignedInt(b0) << 24)
                + ( toUnsignedInt(b1) << 16)
                + ( toUnsignedInt(b2) << 8)
                + toUnsignedInt(b3);
    }

    public static int fourIntsToInt(int i0, int i1, int i2, int i3) {
        return (i0 << 24) + (i1 << 16) + (i2 << 8) + i3;
    }

    public static int[] byteArrToIntArrNoPackPad(byte[] bytes) {
        int[] ints = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            ints[i] = bytes[i];
        }
        return ints;
    }

    public static int[] byteArrToIntArrNoPackPadU(byte[] bytes) {
        int[] ints = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            ints[i] = bytes[i] & 0xFF;
        }
        return ints;
    }

    public static int[] byteArrToIntArrPack(byte[] arr) {
        //BE
        if ((arr.length % 4) != 0) {
            throw new IllegalArgumentException("data len is not divisible by 4");
        }
        int byteOff = 0;
        int intArrLen = arr.length / 4;
        int[] intArr = new int[intArrLen];
        for (int i = 0; i < intArrLen; i++) {
            intArr[i] = fourBytesToInt(
                arr[byteOff],
                arr[byteOff + 1],
                arr[byteOff + 2],
                arr[byteOff + 3]
            );
            byteOff += 4;
        }
        return intArr;
    }

    public static byte[] intArrToByteArr(int[] arr) {
        byte[] byteArray = new byte[arr.length * 4];
        for (int i = 0; i < arr.length; i++) {
            byte[] b4 = toBytes(arr[i]);
            int byteOff = i * 4;
            byteArray[byteOff] = b4[0];
            byteArray[byteOff + 1] = b4[1];
            byteArray[byteOff + 2] = b4[2];
            byteArray[byteOff + 3] = b4[3];
        }
        return byteArray;
    }

    static byte[] intArrToByteArrQuarter(int[] arr, int byteNumZ) {
        byte[] result = new byte[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = (byte) ((arr[i] >> (8 * (3 - byteNumZ))) & 0xFF);
        }
        return result;
    }

    static void readBlock(InputStream is, int readReq, byte[] out) {
        while (readReq != 0) {
            int chunkRead;
            try {
                chunkRead = is.read(out, 0, readReq);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (chunkRead == -1) {
                throw new RuntimeException("can read readReq bytes: EOF");
            }
            readReq -= chunkRead;
        }
    }

    private String bytesHexStr(byte[] arr) {
        StringBuffer sb = new StringBuffer();
        for (byte b : arr) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static String bytesArrToJavaLiteralBytes(byte[] arr) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < arr.length; i++) {
            byte b = arr[i];
            if (b < 0) {
                sb.append("-0x" + Integer.toHexString(0 - b));
            } else {
                sb.append(" 0x" + Integer.toHexString(b));
            }
            if (i != (arr.length - 1)) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public static ArrayList<String> regexFindAll(String text, String pattern) {
        Pattern ptrnObj = Pattern.compile(pattern);
        Matcher mtchrObj = ptrnObj.matcher(text);
        ArrayList<String> matchesStrs = new ArrayList<String>();
        while (mtchrObj.find()) {
            matchesStrs.add(mtchrObj.group());
        }
        return matchesStrs;
    }

    public static String asciiArray(byte[] arr) {
        if (arr == null ) {
            return null;
        }
        return Utils.strDecode(arr, "ascii");
    }

    public static String strDecode(byte[] bytes, String encoding) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        InputStreamReader isr;
        try {
            isr = new InputStreamReader(bais, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        StringBuffer sb = new StringBuffer();
        char[] buff = new char[512];
        int readCnt;

        try {
            while (-1 != (readCnt = isr.read(buff, 0, buff.length))) {
                sb.append(buff, 0, readCnt);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return sb.toString();
    }

    public static byte[] strEncode(String str, String encoding) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw;

        try {
            osw = new OutputStreamWriter(baos, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        try {
            osw.write(str);
            osw.flush();
            osw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static BufferedOutputStream fosFromFname(String fname) {
        return fosFromFname(new File(fname));
    }

    public static BufferedOutputStream fosFromFname(File fname) {
        FileOutputStream fos;
        BufferedOutputStream bos;
        try {
            fos = new FileOutputStream(fname);
            bos = new BufferedOutputStream(fos);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return bos;
    }

    public static float median(float[] a) {
        float[] a2 = Arrays.copyOf(a, a.length);
        Arrays.sort(a2);
        return a2[a2.length / 2];
    }

    private static Runtime identRuntime() {
        String runtimeName = System.getProperty("java.runtime.name");
        String className;
        if ((runtimeName.indexOf("Java(TM)") != -1)
            || (runtimeName.indexOf("OpenJDK") != -1)) {
            return Runtime.JAVA_REAL;
        } else if (runtimeName.indexOf("Android") != -1) {
            return Runtime.ANDROID;
        } else {
            throw new RuntimeException(String.format("unknown runtime. java.runtime.name = \"%s\"", runtimeName));
        }
    }

    static public Class clsByRuntime(String javaClassName, String androidClassName) {
        String className = null;
        Utils.Runtime runtime = Utils.identRuntime();
        if (runtime == Utils.Runtime.JAVA_REAL) {
            className = javaClassName;
        } else if (runtime == Utils.Runtime.ANDROID) {
            className = androidClassName;
        }

        try {
            return Class.forName(className);
        } catch (ReflectiveOperationException e) {
            String s = String.format("can't load \"%s\" through Class.forName", className);
            throw new PlatfImplRuntimeLoadException(s);
        }
    }

    static public String byteArrDmp(byte[] arr, int w) {
        int[] intArr = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            intArr[i] = arr[i] & 0xFF;
        }
        return intArrDmp(intArr, w);
    }

    private static String intArrDmp(int[] arr, int w) {
        int max = arr[0];
        int min = arr[0];
        int maxAbs = 0;
        for (int v : arr) {
            if (v > max) {
                max = v;
            }
            if (v < min) {
                min = v;
            }
        }

        maxAbs = Math.max(Math.abs(min), Math.abs(max));

        int places = 0;
        if (maxAbs == 0) {
            places = 1;
        } else {
            while (maxAbs > 0) {
                places++;
                maxAbs /= 10;
            }
        }

        places++; //sign

        String fmt = String.format("%%%dd", places);

        int h = arr.length / w;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                sb.append(String.format(fmt, arr[(i * w) + j]));
                if (j < (w-1)) {
                    sb.append(" ");
                }

            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String getExcpText(Throwable exp) {
        StringWriter writer = new StringWriter();
        exp.printStackTrace(new PrintWriter(writer, true));
        return writer.getBuffer().toString();
    }

    public static SplitExt splitExt(String path) {
        if (path == null) {
            return null;
        }

        if (path.equals("")) {
            return null;
        }

        int dotIdx = path.lastIndexOf(".");

        if (dotIdx == -1) {
            return new SplitExt(path, null);
        }

        if (dotIdx == (path.length() - 1)) {
            return new SplitExt(path.substring(0, dotIdx), null);
        }

        if (dotIdx == 0) {
            return new SplitExt(null, path.substring(1));
        }

        return new SplitExt(path.substring(0, dotIdx), path.substring(dotIdx + 1));
    }

    public static String getPathExt(String path) {
        SplitExt spl = splitExt(path);
        if (spl == null) {
            return null;
        }
        return spl.ext;
    }

    public static void mkdir(File path) {
        if (!path.exists()) {
            if (!path.mkdir()) {
                throw new RuntimeException(String.format("can't create \"%s\" directory", path));
            }
        }
    }

    public static void chkDirWOrCreate(File path) {
        if (path.exists() && !path.isDirectory()) {
            throw new RuntimeException(String.format("path \"%s\" is not directory", path));
        }

        if (path.exists() && !path.canWrite()) {
            throw new RuntimeException(String.format("directory \"%s\" is not writable", path));
        }

        if (!path.exists()) {
            if (!path.mkdir()) {
                throw new RuntimeException(String.format("can't create \"%s\" directory", path));
            }
        }
    }

    public static byte[] arrConcat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    public static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    public static String regexReplaceFunc(String text, String pattern, Function<Matcher, String> func) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, func.apply(m));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static int readToNull(InputStream is) {
        int totalRead = 0;
        int available;
        int skipped;
        try {
            while ((available = is.available()) != 0) {
                skipped = (int) is.skip(available);
                totalRead += skipped;
            }
            is.close();
            return totalRead;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends Exception, R> R throwChecked(Exception t) throws T {
        throw (T) t;
    }

    public static native int modPowNaCall(int num, int power, int modulus);

    public static int arithmeticHalfUpRound(float f) {
        // it's slow but only used for java implementation in unit-test.
        // native one uses math.h round()
        return new BigDecimal(f).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    public static class SplitExt {
        public String path;
        public String ext;

        public SplitExt(String path, String ext) {
            this.path = path;
            this.ext = ext;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SplitExt splitExt = (SplitExt) o;
            return Objects.equals(path, splitExt.path) &&
                    Objects.equals(ext, splitExt.ext);
        }

        @Override
        public String toString() {
            return "SplitExt{" +
                    "path='" + path + '\'' +
                    ", ext='" + ext + '\'' +
                    '}';
        }
    }

    public static int distance(String str1, String str2) {
        int n = str1.length();
        int m = str2.length();
        int tn = n + 1;
        int tm = m + 1;

        int[] tbl = new int[tn * tm];

        for (int j = 0; j < tm; j++) {
            tbl[j] = j;
        }

        for (int i = 0; i < tn; i++) {
            tbl[i * tm] = i;
        }

        char[] s1 = str1.toCharArray();
        char[] s2 = str2.toCharArray();

        for (int i = 1; i < tn; i++) {
            for (int j = 1; j < tm; j++) {
                int r1 = tbl[((i - 1) * tm) + j] + 1;
                int r2 = tbl[(i * tm) + j - 1] + 1;

                int t = (s1[i - 1] == s2[j - 1]) ? 0 : 1;
                int r3 = tbl[((i - 1) * tm) + j - 1] + t;

                tbl[(tm * i) + j] = min(r1, r2, r3);
            }
        }

        return tbl[(tm * tn) - 1];
    }

    public enum Runtime {JAVA_REAL, ANDROID}

    static {
        LoadLib.loadLib();
    }
}
