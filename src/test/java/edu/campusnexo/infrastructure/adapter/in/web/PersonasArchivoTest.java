package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonasArchivoTest {

    /** A minimal workbook: the shared strings and the first sheet are all the import reads. */
    static byte[] xlsx(String sharedStrings, String sheet) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(sharedStrings.getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    @Test
    @DisplayName("CSV de Excel en español: punto y coma, BOM, comillas y sinónimos de columna")
    void csvFromSpanishExcel() {
        String csv = "﻿Tipo;Código;Nombre completo;Programa\r\n"
                + "estudiante;20231001;\"Pérez; Juan\";Ingeniería de Sistemas\r\n"
                + ";;;\r\n"
                + "docente;D-1;\"Ana \"\"la profe\"\" Ruiz\";Software\r\n";

        List<PersonasArchivo.Fila> filas = PersonasArchivo.leer("lista.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, filas.size(), "la fila vacía se salta");
        assertEquals("Pérez; Juan", filas.get(0).get("nombre"));
        assertEquals("Ingeniería de Sistemas", filas.get(0).get("adscripcion"));
        assertEquals(2, filas.get(0).numero());
        assertEquals("Ana \"la profe\" Ruiz", filas.get(1).get("nombre"));
        assertEquals(4, filas.get(1).numero());
    }

    @Test
    @DisplayName("CSV con comas en Windows-1252 y sin columna tipo")
    void csvWindows1252WithCommas() {
        String csv = "codigo,nombre,departamento\nD-7,Mónica Díaz,Química\n";

        List<PersonasArchivo.Fila> filas = PersonasArchivo.leer("DOCENTES.CSV", csv.getBytes(Charset.forName("windows-1252")));

        assertEquals("Mónica Díaz", filas.getFirst().get("nombre"));
        assertEquals("Química", filas.getFirst().get("adscripcion"));
        assertEquals("", filas.getFirst().get("tipo"));
    }

    @Test
    @DisplayName("XLSX: textos compartidos, texto en línea y códigos escritos como número")
    void xlsxFirstSheet() throws IOException {
        String shared = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>tipo</t></si><si><t>codigo</t></si><si><t>nombre</t></si><si><t>adscripcion</t></si>"
                + "<si><r><t>Juan </t></r><r><t>Pérez</t></r></si><si><t>estudiante</t></si></sst>";
        String sheet = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c>"
                + "<c r=\"C1\" t=\"s\"><v>2</v></c><c r=\"D1\" t=\"s\"><v>3</v></c></row>"
                + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>5</v></c><c r=\"B2\"><v>2.0231001E7</v></c>"
                + "<c r=\"C2\" t=\"s\"><v>4</v></c><c r=\"D2\" t=\"inlineStr\"><is><t>Sistemas</t></is></c></row>"
                + "</sheetData></worksheet>";

        PersonasArchivo.Fila fila = PersonasArchivo.leer("lista.xlsx", xlsx(shared, sheet)).getFirst();

        assertEquals("estudiante", fila.get("tipo"));
        assertEquals("20231001", fila.get("codigo"));
        assertEquals("Juan Pérez", fila.get("nombre"));
        assertEquals("Sistemas", fila.get("adscripcion"));
    }

    @Test
    @DisplayName("Un XLSX con entidades externas no llega a leerlas")
    void xlsxRejectsExternalEntities() throws IOException {
        String sheet = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>&e;</t></is></c></row>"
                + "</sheetData></worksheet>";

        assertThrows(DomainValidationException.class, () -> PersonasArchivo.leer("x.xlsx", xlsx("<sst/>", sheet)));
    }

    @Test
    @DisplayName("Columnas que faltan, formato desconocido y archivo vacío se rechazan con un mensaje claro")
    void rejectsWhatItCannotRead() {
        var sinNombre = assertThrows(DomainValidationException.class,
                () -> PersonasArchivo.leer("a.csv", "codigo;programa\nX;Y\n".getBytes(StandardCharsets.UTF_8)));
        assertTrue(sinNombre.getMessage().contains("codigo, nombre y adscripcion"));
        assertThrows(DomainValidationException.class, () -> PersonasArchivo.leer("a.xls", new byte[] {1, 2}));
        assertThrows(DomainValidationException.class, () -> PersonasArchivo.leer("a.csv", new byte[0]));
        assertThrows(DomainValidationException.class, () -> PersonasArchivo.leer("a.xlsx", new byte[] {1, 2, 3}));
    }
}
