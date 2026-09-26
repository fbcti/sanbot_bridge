/**
 * @file        FileUtils.java
 * @brief       Implements FileUtils utility class.
 */
package com.fbcti.sanbot.bridge.util;

import android.os.Environment;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.system.Os;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class that implements common file-system operations.
 *
 * This class provides methods for safely resolving a relative path inside a directory, creating a
 * directory, and reading or writing binary and UTF-8 text files. File-system errors are reported to
 * callers as @c IOException instances so callers can provide operation-specific logging and result
 * data.
 *
 * @version     1.0.001
 * @date        24 Sep 2026
 * @author      Ferry Blaazer
 * @copyright   2026 FBCTI
 */
public final class FileUtils
{
    /**
     * Data directory name. This is a subdirectory of the external storage directory.
     */
    public static final String BRIDGE_DATA_DIRECTORY = "SanbotBridge";

    /** Buffer size used when reading file contents. */
    private static final int FILE_BUFFER_SIZE = 8192;

    /***********************************************************************************************
     * CONSTRUCTORS
     **********************************************************************************************/

    /**
     * Prevents construction of FileUtils instances.
     */
    private FileUtils() {}

    /***********************************************************************************************
     * PUBLIC METHODS
     **********************************************************************************************/

    /**
     * Ensures that the specified directory exists.
     *
     * The directory and any missing parent directories are created if necessary. An exception is
     * thrown if the directory could not be created or if the path exists but is not a directory.
     *
     * @param   directory       directory to validate or create
     *
     * @throws  IOException     thrown if the directory does not exist and could not be created, or
     *                          if its path is not a directory
     */
    public static void ensureDirectory(@NonNull File directory) throws IOException
    {
        if ((directory.exists() == false) && (directory.mkdirs() == false))
            throw new IOException("Could not create directory " + directory.getAbsolutePath());
        if (directory.isDirectory() == false)
            throw new IOException("Directory path is not a directory " + directory.getAbsolutePath());
    }

    /**
     * Returns the extension of the specified file name.
     *
     * The extension consists of all characters following the final period in the file name. The
     * period itself is not included. Periods occurring in directory names are ignored.
     *
     * @param   filename        file name from which to retrieve extension
     *
     * @return  file extension without leading period, or an empty string
     */
    @NonNull
    public static String getFileExtension(@NonNull String filename)
    {
        int separatorPosition = filename.lastIndexOf(File.separatorChar);
        int extensionPosition = filename.lastIndexOf('.');
        if ((extensionPosition <= separatorPosition) || (extensionPosition == filename.length() - 1)) return "";
        return filename.substring(extensionPosition + 1);
    }

    /**
     * Removes the extension from the specified file name.
     *
     * The final period and all characters following it are removed. Periods occurring in directory
     * names are ignored. The original file name is returned if it does not contain an extension.
     *
     * @param   filename        file name from which to remove extension
     *
     * @return  file name without extension
     */
    @NonNull
    public static String removeFileExtension(@NonNull String filename)
    {
        int separatorPosition = filename.lastIndexOf(File.separatorChar);
        int extensionPosition = filename.lastIndexOf('.');
        if ((extensionPosition <= separatorPosition) || (extensionPosition == filename.length() - 1)) return filename;
        return filename.substring(0, extensionPosition);
    }

    /**
     * Returns the names of all regular files in the specified external storage directory.
     *
     * The directory path is resolved relative to the Android external storage directory. Absolute
     * paths and paths that escape the external storage directory are rejected. Only files located
     * directly in the directory are included; subdirectories are not searched. Directory entries
     * and other non-regular files are ignored. File names are returned in alphabetical order. An
     * empty list is returned if the directory exists but contains no regular files.
     *
     * @param   directory       relative path of directory containing files to list
     * @param   extension       extension without leading period, or @c null to include all files
     *
     * @return  list containing regular file names, or @c null if the directory path is invalid, the
     *          directory does not exist, or the directory could not be listed
     */
    @Nullable
    public static List<String> listFileNames(@NonNull String directory, @Nullable String extension)
    {
        File directoryFile;
        try
        {
            directoryFile = resolveChildFile(Environment.getExternalStorageDirectory(), directory);
        }
        catch (IOException e)
        {
            return null;
        }
        if (directoryFile.exists() == false) return null;

        File[] files = directoryFile.listFiles();
        if (files == null) return null;

        List<String> fileNames = new ArrayList<>();
        for (File file : files)
        {
            if ((file.isFile()) && ((extension == null) || (extension.equalsIgnoreCase(getFileExtension(file.getName())))))
            {
                fileNames.add(file.getName());
            }
        }
        Collections.sort(fileNames);
        return fileNames;
    }

    /**
     * Resolves a relative file path inside the specified directory.
     *
     * Canonical paths are used to ensure that the resolved file remains strictly inside the
     * specified directory. Absolute paths, the directory itself, and paths that escape the
     * directory are rejected.
     *
     * @param   directory       parent directory in which to resolve the file
     * @param   relativePath    relative path of file to resolve
     *
     * @return  canonical File instance representing the resolved file
     *
     * @throws  IOException     thrown if the path is absolute, resolves outside the specified
     *                          directory, or cannot be converted to a canonical path
     */
    @NonNull
    public static File resolveChildFile(@NonNull File directory, @NonNull String relativePath) throws IOException
    {
        File requestedFile = new File(relativePath);
        if (requestedFile.isAbsolute()) throw new IOException("File path must be relative");

        File canonicalDirectory = directory.getCanonicalFile();
        File canonicalFile = new File(canonicalDirectory, relativePath).getCanonicalFile();
        String directoryPath = canonicalDirectory.getPath();
        String filePath = canonicalFile.getPath();
        if ((filePath.equals(directoryPath) == true) || (filePath.startsWith(directoryPath + File.separator) == false))
            throw new IOException("File path must stay inside " + directoryPath);

        return canonicalFile;
    }

    /**
     * Reads the complete contents of the specified binary file.
     *
     * @param   file            file to read
     *
     * @return  byte array containing the complete file contents
     *
     * @throws  IOException     thrown if the file could not be read
     */
    @NonNull
    public static byte[] readFile(@NonNull File file) throws IOException
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (FileInputStream inputStream = new FileInputStream(file))
        {
            byte[] buffer = new byte[FILE_BUFFER_SIZE];
            int byteCount;
            while ((byteCount = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, byteCount);
        }
        return outputStream.toByteArray();
    }

    /**
     * Deletes a regular child file from the specified directory.
     *
     * @param   directory           directory containing the file
     * @param   filename            relative name of the file to delete
     * @param   defaultExtension    extension to try when the exact name does not exist, or @c null
     *
     * The exact stored name is preferred. If it does not identify a regular file and has no
     * extension, @p defaultExtension is appended and that path is tried instead. Canonical path
     * resolution prevents the requested file from escaping @p directory.
     *
     * @throws  IOException         thrown if the file is invalid, missing, or cannot be deleted
     */
    public static void deleteChildFile(@NonNull File directory, @NonNull String filename, @Nullable String defaultExtension) throws IOException
    {
        try
        {
            filename = filename.trim();
            if (filename.isEmpty()) throw new IOException("File name is required");

            File file = resolveChildFile(directory, filename);
            if ((file.isFile() == false) && (defaultExtension != null) &&
                (defaultExtension.trim().isEmpty() == false) &&
                (filename.lastIndexOf('.') <= filename.lastIndexOf(File.separatorChar)))
            {
                String extension = defaultExtension.trim();
                if (extension.startsWith(".")) extension = extension.substring(1);
                file = resolveChildFile(directory, filename + "." + extension);
            }

            if (file.isFile() == false) throw new IOException("File not found: " + file.getAbsolutePath());
            if (file.delete() == false) throw new IOException("Could not delete file: " + file.getAbsolutePath());
        }
        catch (SecurityException e)
        {
            throw new IOException("Could not delete file", e);
        }
    }

    /**
     * Deletes all regular files directly inside the specified directory.
     *
     * @param   directory       directory from which to delete regular files
     *
     * A missing directory is treated as empty. Subdirectories and their contents are preserved.
     * Every regular file is attempted, and the returned set contains the absolute paths of files
     * that could not be deleted.
     *
     * @return  absolute paths of regular files that could not be deleted
     *
     * @throws  IOException     thrown if the directory path is invalid or cannot be listed
     */
    @NonNull
    public static Set<String> deleteRegularFiles(@NonNull File directory) throws IOException
    {
        try
        {
            if (directory.exists() == false) return new LinkedHashSet<>();
            if (directory.isDirectory() == false)
                throw new IOException("Directory path is not a directory: " + directory.getAbsolutePath());

            File[] files = directory.listFiles();
            if (files == null) throw new IOException("Could not list directory: " + directory.getAbsolutePath());

            Set<String> failedFiles = new LinkedHashSet<>();
            for (File file : files)
            {
                if ((file.isFile()) && (file.delete() == false)) failedFiles.add(file.getAbsolutePath());
            }
            return failedFiles;
        }
        catch (SecurityException e)
        {
            throw new IOException("Could not delete files from " + directory.getAbsolutePath(), e);
        }
    }

    /**
     * Checks whether a filesystem has enough available space for an estimated file size.
     *
     * @param   filesystemPath      path located on the filesystem to inspect
     * @param   estimatedBytes      estimated file size in bytes
     * @param   requiredPercent     required percentage of the estimate, for example 150
     *
     * @return  boolean specifying if space of sufficient, @c null if check failed
     */
    @Nullable
    public static Boolean checkAvailableSpace(@NonNull File filesystemPath, long estimatedBytes, int requiredPercent)
    {
        if ((estimatedBytes < 0L) || (requiredPercent <= 0)) return null;

        long requiredBytes = estimatedBytes + estimatedBytes/2L;
        StatFs storageStats = new StatFs(filesystemPath.getAbsolutePath());
        return (storageStats.getAvailableBytes() > requiredBytes);
    }

    /**
     * Moves and optionally renames a file.
     *
     * @param   sourceFile      Java @c File instance representing file to move
     * @param   directory       Java @c File instance representing target directory
     * @param   fileName        target file name
     *
     * If @p fileName is @c null or blank the file retains its original name.
     *
     * @return  Java @c File instance representing moved file, or @c null on failure
     */
    @Nullable
    public static File moveFile(@NonNull File sourceFile, File directory, String fileName)
    {
        try
        {
            ensureDirectory(directory);
            if ((fileName == null) || (fileName.trim().isEmpty())) fileName = sourceFile.getName();
            File targetFile = FileUtils.resolveChildFile(directory, fileName);
            Os.rename(sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
            return targetFile;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Reads the complete contents of the specified UTF-8 text file.
     *
     * @param   file            file to read
     *
     * @return  string containing the complete file contents
     *
     * @throws  IOException     thrown if the file could not be read
     */
    @NonNull
    public static String readTextFile(@NonNull File file) throws IOException
    {
        return new String(readFile(file), StandardCharsets.UTF_8);
    }

    /**
     * Writes binary data to the specified file.
     *
     * Existing file contents are replaced. The parent directory must already exist.
     *
     * @param   file            file to write
     * @param   data            binary data to write
     *
     * @throws  IOException     thrown if the file could not be written
     */
    public static void writeFile(@NonNull File file, @NonNull byte[] data) throws IOException
    {
        try (FileOutputStream outputStream = new FileOutputStream(file, false))
        {
            outputStream.write(data);
            outputStream.flush();
        }
    }

    /**
     * Writes UTF-8 text to the specified file.
     *
     * Existing file contents are replaced. The parent directory must already exist.
     *
     * @param   file            file to write
     * @param   text            text to write
     *
     * @throws  IOException     thrown if the file could not be written
     */
    public static void writeTextFile(@NonNull File file, @NonNull String text) throws IOException
    {
        writeFile(file, text.getBytes(StandardCharsets.UTF_8));
    }
}