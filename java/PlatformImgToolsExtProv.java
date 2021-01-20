package com.drscbt.shared.piclib;

import com.drscbt.shared.utils.Utils;

import java.lang.reflect.Constructor;

public class PlatformImgToolsExtProv {
    static private PlatformImgToolsExtInterface getPlatfImgToolsExt() {
        if (PlatformImgToolsExtProv.toolsExt == null) {
            Class cls = Utils.clsByRuntime(
                "com.drscbt.platf_j.PlatformImgToolsExtJava",
                "com.drscbt.platf_a.PlatformImgToolsExtAndroid"
            );
            try {
                Constructor<?> ctor = cls.getDeclaredConstructor();
                PlatformImgToolsExtProv.toolsExt = (PlatformImgToolsExtInterface)ctor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        return PlatformImgToolsExtProv.toolsExt;
    }

    static public ImgTools getTools() {
        if (PlatformImgToolsExtProv.tools == null) {
            PlatformImgToolsExtProv.tools = new ImgTools(getPlatfImgToolsExt());
        }
        return PlatformImgToolsExtProv.tools;
    }

    static private PlatformImgToolsExtInterface toolsExt;
    static private ImgTools tools;
}
