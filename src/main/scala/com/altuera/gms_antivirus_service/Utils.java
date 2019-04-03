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
        .collect(Collectors.toMap(h -> h, request::getHeader));
    }

    log.trace("получили заголовки {}", headers.entrySet().stream().map(entry -> String.join(":", entry.getKey(), entry.getValue())).collect(Collectors.joining("|")));
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
    log.trace("создали временную директорию {} внутри базовой директории {}",
      tempDirectory, baseDir);
    if (tempDirectory != null) {
      File tempFile = new File(tempDirectory.getAbsolutePath() + File.separator + fileName);
      log.trace("создали временный файл {} внутри временной директории ", tempFile);
      return tempFile;
    }

    log.error("ошибка создания файла или директории. Базовая директория {} имя файла {}",
      baseDir, fileName);
    throw new CreateTempDirAndTempFileException("Could not create temporary file or directory", null);
  }

  /**
   * Создает временную директорию внутри базовой директории baseDir
   *
   * @param baseDir
   * @return
   */
  private static File createTempDirectoryInsideBaseDir(File baseDir) throws CreateTempDirAndTempFileException {

    String baseName = System.currentTimeMillis() + "-";
    //log.trace("базовое имя временной директории {}", baseName);

    for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
      File tempDir = new File(baseDir, baseName + counter);
      if (tempDir.mkdir()) {
        //log.trace("окончательное имя временной директории {}", tempDir);
        //log.trace("число попыток подбора уникального имени {}", counter + 1);
        return tempDir;
      }
    }
    log.error("не удалось создать директорию с базовыми именем {} Количество выполненных попыток {}",
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
    log.trace("удаляем директорию {}", directory);
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
      log.trace("файл с именем {} не имеет расширения", fileName);
      return "";
    } else {
      String fileExtension = fileName.substring(lastIndex + 1);
      //log.trace("получили расширение {} для имени файла {} ", fileExtension, fileName);
      return fileExtension;
    }
  }

  /**
   * Создаём директорию со случайным уникальным именем внутри директории baseDirectoryForTemporaryDir
   * Копируем файл sourceFile в созданную директорию.
   *
   * @param sourceFile                   исходный файл для копирования
   * @param baseDirectoryForTemporaryDir базовая директория
   * @return копия исходного файла
   */
  public static File copyFileToSeparateTempDir(File sourceFile, File baseDirectoryForTemporaryDir) throws IOException, CreateTempDirAndTempFileException {
    File targetFile = null;
    try {
      targetFile = createNewTempDirAndTempFileInDir(baseDirectoryForTemporaryDir, sourceFile.getName());
      Files.copy(Paths.get(sourceFile.getPath()), Paths.get(targetFile.getPath()), StandardCopyOption.REPLACE_EXISTING);
    } catch (CreateTempDirAndTempFileException ex) {
      log.error("ошибка создания временной директории", ex);
      throw ex;
    } catch (IOException ex) {
      log.error("ошибка копирования файла", ex);
      throw ex;
    }
    if (targetFile != null) {
      return targetFile;
    } else {
      log.error("не удалось создать файл {}", targetFile.getPath());
      throw new IllegalStateException("не удалось создать файл");
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
    log.trace("выполняем поиск файла внутри временной директории {} расположенной в {}",
      tempDirName, baseDirectoryForTemporaryDir);
    File dir = Paths.get(baseDirectoryForTemporaryDir.getAbsolutePath() + File.separator + tempDirName).toFile();
    log.trace("конечная директория для поиска {}", dir);
    if (dir != null && dir.exists()) {
      File[] files = dir.listFiles();
      if (files.length > 0) {
        log.trace("внутри директории {} найдено {} файлов. будет извлечен файл {}", dir, files.length, files[0]);
        return Optional.of(files[0]);
      } else {
        log.trace("внутри директории {} не найдено файлов", dir);
      }
    } else {
      log.trace("директории {} не существует", dir);
    }
    return Optional.empty();
  }

}
