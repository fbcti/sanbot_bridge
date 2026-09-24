/**
 * @file        FileUtils.java
 * @brief       Implements FileUtils utility class.
 */
package com.fbcti.sanbot.bridge.util;

import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * @return  file extension without leading period, or an empty string if the file name does not
     *          contain an extension
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
