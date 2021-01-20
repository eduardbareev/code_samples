package com.drscbt.andrapp.conf;

import com.drscbt.andrapp.URIFileLoader;
import com.drscbt.shared.utils.UncheckedFileNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class ConfLoader {
    private static Logger _log = LoggerFactory.getLogger(ConfLoader.class);
    private static Conf _instance;

    public static Conf getConf() {
        if (ConfLoader._instance == null) {
            URIFileLoader loader = new URIFileLoader();

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
            Conf conf;

            InputStream is = loader.load(StaticConf.getInstance().configDefaultPath);
            try {
                conf = mapper.readValue(is, Conf.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            URI extPath = StaticConf.getInstance().configExtPath;

            try {
                // readerForUpdating() ignores yaml errors
                InputStream inpStreamExt = loader.load(extPath);
                InputStream inpStreamExtAgain = loader.load(extPath);
                mapper.readValue(inpStreamExt, Conf.class); // just validating, ignoring result
                mapper.readerForUpdating(conf).readValue(inpStreamExtAgain);
                ConfLoader._log.debug("merged configuration from {}", extPath);
            } catch (UncheckedFileNotFoundException e) {
                // this file is optional
            } catch (IOException e) {
                throw new RuntimeException(String.format("error while reading %s. %s", extPath, e.getMessage()), e);
            }

            ConfLoader._instance = conf;
        }
        return ConfLoader._instance;
    }
}
