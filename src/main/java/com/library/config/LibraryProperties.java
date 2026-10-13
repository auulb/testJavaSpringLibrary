package com.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 绑定 application.yml 中 library.* 的配置。
 */
@ConfigurationProperties(prefix = "library")
public class LibraryProperties {

    /** JSON 数据文件所在目录。 */
    private String dataDir = "data";

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Path dataDirPath() {
        return Path.of(dataDir);
    }
}
