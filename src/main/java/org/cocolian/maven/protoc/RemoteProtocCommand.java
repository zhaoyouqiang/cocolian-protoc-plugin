package org.cocolian.maven.protoc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

public class RemoteProtocCommand implements ProtocCommand
{
  private static final Logger log = Logger.getLogger(RemoteProtocCommand.class);
  // 存储protoc.exe文件的临时文件夹名称
  private static final String PROTOCJAR = "protoc";
  // 远程下载地址
  private static final String RELEASEURLSTR = "http://static.cocolian.org/protoc/";
  private static final String SEPARATOR = "/";

  @Override
  public String make(String version, String os, String arch)
  {
    log.debug("protoc version: " + version + ", jplatform os: " + os + ", platform instruction set: " + arch);
    ProtocVersion protocVersion = new ProtocVersion(null, null, version);
    try
    {
      // 创建protoc文件的临时文件夹
      // File tmpDir = File.createTempFile(PROTOCJAR, "");
      // Files.delete(Paths.get(tmpDir.getAbsolutePath()));
      // tmpDir.mkdirs();
      // tmpDir.deleteOnExit();
      // File binDir = new File(tmpDir, "bin");
      // binDir.mkdirs();
      // binDir.deleteOnExit();

      // 先在本地缓存目录中搜索
      File exeFile = findDownloadProtoc(protocVersion);
      if (exeFile == null)
      {
        throw new FileNotFoundException("Unsupported platform: " + getProtocExeName(protocVersion));
      }

      // File protocTemp = new File(binDir, "protoc.exe");
      // ProtocUtils.populateFile(exeFile.getAbsolutePath(), protocTemp);
      // log.debug("tempFile: "+protocTemp.getAbsolutePath());
      // boolean setExecutable = protocTemp.setExecutable(true);
      // log.debug("setExecutable:"+setExecutable);
      // protocTemp.deleteOnExit();
      return exeFile.getAbsolutePath();
    } catch (IOException e)
    {
      log.error(e.getMessage(), e);
    }

    return null;
  }

  /**
   * 查找下载Protoc文件
   * 
   * @param protocVersion Protoc
   * @return
   * @throws IOException
   */
  private File findDownloadProtoc(ProtocVersion protocVersion) throws IOException
  {
    // 先查找本地缓存目录
    try
    {
      File exeFile = downloadProtoc(protocVersion, false);
      if (exeFile != null)
      {
        if (!exeFile.canExecute())
        {
          boolean setExecutable = exeFile.setExecutable(true);
          log.debug(exeFile.getName() + " Executable: " + setExecutable);
        }
        return exeFile;
      }
    } catch (IOException e)
    {
      log.error(e.getMessage(), e);
    }

    // 从远程服务器下载Protoc文件
    try
    {
      File exeFile = downloadProtoc(protocVersion, true);
      if (exeFile != null)
      {
        if (!exeFile.canExecute())
        {
          boolean setExecutable = exeFile.setExecutable(true);
          log.debug(exeFile.getName() + " Executable: " + setExecutable);
        }
        return exeFile;
      }
    } catch (IOException e)
    {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * 下载protoc文件
   * 
   * @param protocVersion 版本
   * @param trueDownload true：从远程下载；false：从本地缓存加载
   * @return File
   * @throws IOException
   */
  private File downloadProtoc(ProtocVersion protocVersion, boolean trueDownload) throws IOException
  {

    File webcacheDir = getWebcacheDir();

    // download exe
    String exeSubPath = protocVersion.mVersion + SEPARATOR + getProtocExeName(protocVersion);
    URL exeUrl = new URL(RELEASEURLSTR + exeSubPath);
    File exeFile = new File(webcacheDir, exeSubPath);
    if (trueDownload)
    {
      return downloadFile(exeUrl, exeFile, 0);
    } else if (exeFile.exists())
    { // cache only
      log.debug("cached: " + exeFile);
      return exeFile;
    }
    return null;
  }

  /**
   * 获取缓存路径
   * 
   * @return
   * @throws IOException
   */
  private static File getWebcacheDir() throws IOException
  {
    File tmpFile = File.createTempFile(PROTOCJAR, ".tmp");
    File cacheDir = new File(tmpFile.getParentFile(), PROTOCJAR + ".webcache");
    cacheDir.mkdirs();
    Files.delete(Paths.get(tmpFile.getAbsolutePath()));
    return cacheDir;
  }

  /**
   * 下载文件
   * 
   * @param srcUrl 下载地址
   * @param destFile 保存到
   * @param cacheTime 缓存时间
   * @return
   * @throws IOException
   */
  private File downloadFile(URL srcUrl, File destFile, long cacheTime) throws IOException
  {
    if (destFile.exists() && ((cacheTime <= 0) || (System.currentTimeMillis() - destFile.lastModified() <= cacheTime)))
    {
      log.debug("cached: " + destFile);
      return destFile;
    }

    File tmpFile = File.createTempFile(PROTOCJAR, ".tmp");

    URLConnection con = srcUrl.openConnection();
    con.setRequestProperty("User-Agent", "Mozilla"); // sonatype only returns proper
                                                     // maven-metadata.xml if this is set
    try (FileOutputStream os = new FileOutputStream(tmpFile); InputStream is = con.getInputStream();)
    {
      log.debug("downloading: " + srcUrl);
      ProtocUtils.streamCopy(is, os);
      destFile.getParentFile().mkdirs();
      // 判断exe文件是否存在
      if (destFile.exists())
      {
        Files.delete(Paths.get(destFile.getAbsolutePath()));
      }
      FileUtils.copyFile(tmpFile, destFile);
      log.debug("tmpFile:" + tmpFile.exists());
      log.debug("destFile:" + destFile.exists());
      boolean setLastModified = destFile.setLastModified(System.currentTimeMillis());
      log.debug("setLastModified:" + setLastModified);
    } catch (IOException e)
    {
      log.error(e.getMessage(), e);
      Files.delete(Paths.get(tmpFile.getAbsolutePath()));
      if (!destFile.exists())
        throw e; // if download failed but had cached version, ignore exception
    }
    log.debug("saved: " + destFile);
    return destFile;
  }



  /**
   * 获取protoc扩展名
   * 
   * @param protocVersion
   * @return
   */
  private String getProtocExeName(ProtocVersion protocVersion)
  {
    return "protoc-" + protocVersion.mVersion + "-" + new BasicPlatformDetector().getClassfier() + ".exe";
  }


}
