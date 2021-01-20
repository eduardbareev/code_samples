package com.drscbt.andrapp.conf;

import com.drscbt.andrapp.URIFileLoader;
import com.drscbt.shared.utils.UncheckedFileNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DevDB {
    private Logger _log = LoggerFactory.getLogger(DevDB.class);

    private Map<String, DevDBInfo> _map;

    private Map<String, DevDBInfo> loadFile(URI u) {
        InputStream inpStream;
        inpStream = new URIFileLoader().load(u);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        TypeFactory typeFactory = mapper.getTypeFactory();
        MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, DevDBInfo.class);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

        try {
            return mapper.readValue(inpStream, mapType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    DevDB(List<URI> devDbPath) {
        this._map = new HashMap<String, DevDBInfo>();
        for (URI u : devDbPath) {
            try {
                Map<String, DevDBInfo> map = this.loadFile(u);
                for (String s : map.keySet()) {
                    this._log.debug("loaded {} device info from {}", s, u);
                }
                this._map.putAll(map);
            } catch (UncheckedFileNotFoundException e) {
                // skip file
            }
        }
    }

    DevDBInfo getDevInfo(String id) {
        DevDBInfo i = this._map.get(id);
        if (i == null) {
            throw new RuntimeException(String.format("can't find dev info for \"%s\"", id));
        }

        return i;
    }
}
