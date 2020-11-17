package com.danikula.videocache.file;

import android.text.TextUtils;

import com.danikula.videocache.ProxyCacheUtils;

/**
 * Implementation of {@link FileNameGenerator} that uses MD5 of url as file name
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
public class Md5FileNameGenerator implements FileNameGenerator {

    private static final int MAX_EXTENSION_LENGTH = 4;

    @Override
    public String generate(String id, String url) {
        String extension = getExtension(url);
        String name = ProxyCacheUtils.computeMD5(id);
        return TextUtils.isEmpty(extension) ? name : name + "." + extension;
    }

    private String getExtension(String url) {
        int dotIndex = url.lastIndexOf('.');
        int slashIndex = url.lastIndexOf('/');
        int qmarkIndex = url.lastIndexOf("?");
        if (qmarkIndex != -1 && dotIndex != -1 && dotIndex > slashIndex && qmarkIndex > dotIndex) {
            return url.substring(dotIndex + 1, qmarkIndex);
        }
        return dotIndex != -1 && dotIndex > slashIndex && dotIndex + 2 + MAX_EXTENSION_LENGTH > url.length() ?
                url.substring(dotIndex + 1, url.length()) : "";
    }
}
