package com.altuera.gms_antivirus_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
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
			headers = Collections.list(request.getHeaderNames())
					.stream()
					.collect(Collectors.toMap(h -> h, request::getHeader));
		}
		log.trace(String.valueOf(headerNames));
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
		if (tempDirectory != null) {
			File tempFile = new File(tempDirectory.getAbsolutePath() + File.separator + fileName);
			return tempFile;
		}
		throw new CreateTempDirAndTempFileException("Could not create temporary file or directory");
	}

	/**
	 * Создает временную директорию внутри базовой директории baseDir
	 *
	 * @param baseDir
	 * @return
	 */
	private static File createTempDirectoryInsideBaseDir(File baseDir) throws CreateTempDirAndTempFileException {

		String baseName = System.currentTimeMillis() + "-";

		for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
			File tempDir = new File(baseDir, baseName + counter);
			if (tempDir.mkdir()) {
				return tempDir;
			}
			log.error("Failed to create directory within {} attempts (tried {} 0 to {} {})",
					TEMP_DIR_ATTEMPTS, baseName, baseName, TEMP_DIR_ATTEMPTS - 1);
		}
		throw new CreateTempDirAndTempFileException("Failed to create directory ");
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
		log.trace("delete folder {}", directory);
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
}
