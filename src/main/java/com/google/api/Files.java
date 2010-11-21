/**
 * Copyright (C) 2009,  Richard Midwinter
 *
 * Stands under LGPL. See license.txt
 */
package com.google.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Files {

    private Files() {
    }

    /**
     * Writes a String to a given file.
     *
     * @param file The file to write to.
     * @param content The text to write to the given file.
     * @throws IOException Thrown on IO errors.
     */
    public static void write(final File file, final String content) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(content);
        bw.close();
    }

    /**
     * Reads a file to a String.
     * @param file The file to read from.
     * @return The content of the file as a String.
     * @throws IOException Thrown on IO errors.
     */
    public static String read(final File file) throws IOException {
        final StringBuilder sb = new StringBuilder();
        String line;

        final BufferedReader br = new BufferedReader(new FileReader(file));
        while ((line = br.readLine()) != null) {
            sb.append(line);
            sb.append('\n');
        }

        return sb.toString();
    }
}
