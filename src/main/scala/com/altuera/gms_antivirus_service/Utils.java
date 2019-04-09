// © LLC "Altuera", 2019
package com.altuera.gms_antivirus_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;


public class Utils {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * Maximum loop count when creating temp directories.
   */
  private static final int TEMP_DIR_ATTEMPTS = 10000;

  /**
   * копирует заголовки запроса в Map<String, String>
   *
   * @param request
   * @return
   */
  public static Map<String, String> getRequestHeaders(HttpServletRequest request) {
    Enumeration<String> headerNames = request.getHeaderNames();
    Map<String, String> headers = new HashMap<>();
    if (headerNames != null) {
      headers = Collections.list(headerNames)
        .stream()
        .collect(Collectors.toMap(h -> h.toLowerCase(), request::getHeader));
    }

    log.trace("received {} headers", headers.entrySet().stream().map(entry -> String.join(":", entry.getKey(), entry.getValue())).collect(Collectors.joining("|")));
    return headers;
  }

  /**
   * создает внутри baseDir временную директорию, а в этой временной директории создает файл
   *
   * @param baseDir  базовая директория
   * @param fileName имя временного файла
   * @return временный файл
   * @throws CreateTempDirAndTempFileException
   */
  public static File createNewTempDirAndTempFileInDir(File baseDir, String fileName) throws CreateTempDirAndTempFileException {
    File tempDirectory = createTempDirectoryInsideBaseDir(baseDir);
    log.trace("created a temporary directory {} inside the base directory {}",
      tempDirectory, baseDir);
    if (tempDirectory != null) {
      File tempFile = new File(tempDirectory.getAbsolutePath() + File.separator + fileName);
      log.trace("created a temporary file {} inside the temporary directory", tempFile);
      return tempFile;
    }

    log.error("Error creating file or directory. Base directory {} file name {}",
      baseDir, fileName);
    throw new CreateTempDirAndTempFileException("Could not create temporary file or directory", null);
  }

  /**
   * Создает временную директорию внутри базовой директории baseDir
   *
   * @param baseDir
   * @return
   */
  public static File createTempDirectoryInsideBaseDir(File baseDir) throws CreateTempDirAndTempFileException {

    String baseName = System.currentTimeMillis() + "-";
    //log.trace("базовое имя временной директории {}", baseName);

    for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
      File tempDir = new File(baseDir, baseName + counter);
      if (tempDir.mkdirs()) {
        //log.trace("окончательное имя временной директории {}", tempDir);
        //log.trace("число попыток подбора уникального имени {}", counter + 1);
        return tempDir;
      }
    }
    log.error("unable to create directory with base name {} Number of retries {}",
      baseName, TEMP_DIR_ATTEMPTS - 1);
    throw new CreateTempDirAndTempFileException("Failed to create directory ", null);
  }

  /**
   * Проверяет наличие директории с данным pathname
   * Если такой директории нет, то создаём её
   *
   * @param pathname
   * @return
   */
  public static File createDirIfNotExist(String pathname) {
    File dir = new File(pathname);
    dir.mkdir();
    return dir;
  }

  /**
   * Метод удаляет рекурсивно все содержимое указанной директории
   * по завершении выполнения
   *
   * @param directory
   */
  public static void deleteFolderRecursively(File directory) {
    log.trace("delete directory {}", directory);
    if (directory != null && directory.exists() && directory.isDirectory()) {
      File[] files = directory.listFiles();
      for (File file : files) {
        if (file.isDirectory()) {
          deleteFolderRecursively(file);
        } else {
          file.deleteOnExit();
        }
      }
      directory.deleteOnExit();
    }
  }

  public static String extractFileExt(String fileName) {
    int lastIndex = fileName.lastIndexOf(".");
    if (lastIndex == -1) {
      log.trace("file named {} does not have an extension", fileName);
      return "";
    } else {
      String fileExtension = fileName.substring(lastIndex + 1);
      //log.trace("получили расширение {} для имени файла {} ", fileExtension, fileName);
      return fileExtension;
    }
  }

  /**
   * Копируем файл sourceFile в директорию targetDir
   * @param sourceFile исходный файл для копирования
   * @param targetDir директория в которую должна быть помещена копия файла
   * @return копия исходного файла
   */
  public static File copyFileToDir(File sourceFile, File targetDir) throws IOException, CreateTempDirAndTempFileException {
    File targetFile = null;
    try {
      if (targetDir != null && targetDir.exists() && targetDir.isDirectory()) {
        targetFile = new File(targetDir.getAbsolutePath() + File.separator + sourceFile.getName());
        Files.copy(Paths.get(sourceFile.getPath()), Paths.get(targetFile.getPath()), StandardCopyOption.REPLACE_EXISTING);
        log.trace("target file = {}", targetFile);
      } else {
        log.trace("target dir == null, not exist, or not directory");
      }
    } catch (IOException ex) {
      log.error("file copy error", ex);
      throw ex;
    }
    if (targetFile != null) {
      return targetFile;
    } else {
      log.error("failed to create file");
      throw new IllegalStateException("failed to create file");
    }
  }

  /**
   * Получаем файл из хранилища оригинальных файлов.
   * Предполагается, что внутри директории baseDirectoryForTemporaryDir
   * находятся временные каталоги с именами вида 1553873076585-0
   * внутри каждого из таких временных каталогов содержится 1 файл.
   * Чтобы получить этот файл достаточно знать baseDirectoryForTemporaryDir и имя временной директории
   *
   * @param baseDirectoryForTemporaryDir базовая директория в которой выполняется поиск
   * @param tempDirName                  имя директории в которой содержится интересущий нас файл
   * @return файл наденный внутри tempDirName
   */
  public static Optional<File> getFileByBaseDirAndSeparateTempDirName(File baseDirectoryForTemporaryDir, String tempDirName) {
    log.trace("perform a file search inside the {} temporary directory located in {}",
      tempDirName, baseDirectoryForTemporaryDir);
    File dir = Paths.get(baseDirectoryForTemporaryDir.getAbsolutePath() + File.separator + tempDirName).toFile();
    log.trace("destination directory for search {}", dir);
    if (dir != null && dir.exists()) {
      File[] files = dir.listFiles();
      if (files.length > 0) {
        log.trace("found {} files. file {} will be extracted", files.length, files[0]);
        return Optional.of(files[0]);
      } else {
        log.trace("no files found in directory {}", dir);
      }
    } else {
      log.trace("directory {} does not exist", dir);
    }
    return Optional.empty();
  }

}
