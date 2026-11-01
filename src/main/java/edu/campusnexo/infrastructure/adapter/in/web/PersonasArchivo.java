package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Rows of a people file for the bulk import: a CSV (comma, semicolon or tab; UTF-8 or Windows-1252, as Excel saves it
 * in Spanish) or the first sheet of an XLSX. The first row names the columns; the names are matched without accents or
 * case, with their usual synonyms. XLSX is read with the JDK alone: no external entities and a cap on the unzipped
 * size, so a crafted file cannot reach the network or exhaust memory.
 */
final class PersonasArchivo {
    static final int MAX_FILAS = 5_000;
    private static final long MAX_DESCOMPRIMIDO = 20L * 1024 * 1024;
    private static final Map<String, String> COLUMNAS = Map.ofEntries(
            Map.entry("tipo", "tipo"), Map.entry("rol", "tipo"),
            Map.entry("codigo", "codigo"), Map.entry("codigo institucional", "codigo"), Map.entry("code", "codigo"),
            Map.entry("id", "codigo"), Map.entry("documento", "codigo"),
            Map.entry("nombre", "nombre"), Map.entry("nombres", "nombre"), Map.entry("nombre completo", "nombre"),
            Map.entry("name", "nombre"),
            Map.entry("adscripcion", "adscripcion"), Map.entry("programa", "adscripcion"),
            Map.entry("departamento", "adscripcion"), Map.entry("facultad", "adscripcion"),
            Map.entry("dependencia", "adscripcion"));

    /** One data row: its number in the file (the header is row 1) and its cells by canonical column. */
    record Fila(int numero, Map<String, String> celdas) {
        String get(String columna) {
            String valor = celdas.get(columna);
            return valor == null ? "" : valor.trim();
        }
    }

    private PersonasArchivo() {
    }

    static List<Fila> leer(String nombreArchivo, byte[] contenido) {
        String nombre = nombreArchivo == null ? "" : nombreArchivo.toLowerCase(Locale.ROOT);
        List<List<String>> tabla;
        if (nombre.endsWith(".xlsx")) {
            tabla = xlsx(contenido);
        } else if (nombre.endsWith(".csv") || nombre.endsWith(".txt")) {
            tabla = csv(texto(contenido));
        } else {
            throw new DomainValidationException("El archivo debe ser .csv o .xlsx (un .xls antiguo se guarda primero como .xlsx)");
        }
        if (tabla.isEmpty()) {
            throw new DomainValidationException("El archivo está vacío");
        }
        List<String> encabezado = tabla.getFirst().stream().map(PersonasArchivo::columna).toList();
        if (!encabezado.contains("codigo") || !encabezado.contains("nombre") || !encabezado.contains("adscripcion")) {
            throw new DomainValidationException("La primera fila debe nombrar las columnas codigo, nombre y adscripcion "
                    + "(o programa / departamento), y opcionalmente tipo");
        }
        if (tabla.size() - 1 > MAX_FILAS) {
            throw new DomainValidationException("El archivo tiene más de " + MAX_FILAS + " filas; divídelo en partes");
        }
        List<Fila> filas = new ArrayList<>();
        for (int i = 1; i < tabla.size(); i++) {
            List<String> valores = tabla.get(i);
            if (valores.stream().allMatch(v -> v == null || v.isBlank())) {
                continue;
            }
            Map<String, String> celdas = new HashMap<>();
            for (int c = 0; c < encabezado.size() && c < valores.size(); c++) {
                if (encabezado.get(c) != null) {
                    celdas.put(encabezado.get(c), valores.get(c));
                }
            }
            filas.add(new Fila(i + 1, celdas));
        }
        return filas;
    }

    /** Canonical name of a header cell, or null for a column the import ignores. */
    private static String columna(String encabezado) {
        String limpio = Normalizer.normalize(encabezado == null ? "" : encabezado, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        return COLUMNAS.get(limpio);
    }

    // ---- CSV ---------------------------------------------------------------------------------------------------

    /** UTF-8 when the bytes are valid UTF-8, otherwise Windows-1252 (Excel's CSV in Spanish). */
    private static String texto(byte[] contenido) {
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(contenido)).toString();
        } catch (CharacterCodingException notUtf8) {
            texto = new String(contenido, Charset.forName("windows-1252"));
        }
        return texto.startsWith("﻿") ? texto.substring(1) : texto;
    }

    /** RFC 4180 with the separator of the header row: quoted fields may hold separators, quotes and line breaks. */
    static List<List<String>> csv(String texto) {
        int finEncabezado = texto.indexOf('\n') < 0 ? texto.length() : texto.indexOf('\n');
        String encabezado = texto.substring(0, finEncabezado);
        char separador = ';';
        long puntoYComa = encabezado.chars().filter(c -> c == ';').count();
        if (encabezado.chars().filter(c -> c == '\t').count() > puntoYComa) {
            separador = '\t';
        } else if (encabezado.chars().filter(c -> c == ',').count() > puntoYComa) {
            separador = ',';
        }
        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (comillas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    comillas = false;
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                comillas = true;
            } else if (c == separador) {
                fila.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n') {
                fila.add(campo.toString());
                campo.setLength(0);
                filas.add(fila);
                fila = new ArrayList<>();
            } else if (c != '\r') {
                campo.append(c);
            }
        }
        if (!campo.isEmpty() || !fila.isEmpty()) {
            fila.add(campo.toString());
            filas.add(fila);
        }
        return filas;
    }

    // ---- XLSX --------------------------------------------------------------------------------------------------

    private static List<List<String>> xlsx(byte[] contenido) {
        byte[] compartidas = null;
        byte[] hoja = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (entrada.getName().equals("xl/sharedStrings.xml")) {
                    compartidas = limitado(zip);
                } else if (entrada.getName().equals("xl/worksheets/sheet1.xml")) {
                    hoja = limitado(zip);
                }
            }
        } catch (IOException damaged) {
            throw new DomainValidationException("El .xlsx está dañado o no es un libro de Excel");
        }
        if (hoja == null) {
            throw new DomainValidationException("El .xlsx no tiene una primera hoja (xl/worksheets/sheet1.xml)");
        }
        try {
            List<String> textos = compartidas == null ? List.of() : textosCompartidos(compartidas);
            return filasDeHoja(hoja, textos);
        } catch (XMLStreamException | RuntimeException malformed) {
            if (malformed instanceof DomainValidationException domain) {
                throw domain;
            }
            throw new DomainValidationException("No se pudo leer la hoja del .xlsx: " + malformed.getMessage());
        }
    }

    private static byte[] limitado(InputStream entrada) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] bloque = new byte[8192];
        long total = 0;
        int leidos;
        while ((leidos = entrada.read(bloque)) > 0) {
            total += leidos;
            if (total > MAX_DESCOMPRIMIDO) {
                throw new DomainValidationException("El .xlsx descomprimido supera 20 MB");
            }
            salida.write(bloque, 0, leidos);
        }
        return salida.toByteArray();
    }

    private static XMLStreamReader lector(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    /** Each {@code <si>} is one shared string, possibly split into rich-text runs. */
    private static List<String> textosCompartidos(byte[] xml) throws XMLStreamException {
        List<String> textos = new ArrayList<>();
        XMLStreamReader r = lector(xml);
        StringBuilder actual = null;
        while (r.hasNext()) {
            int evento = r.next();
            if (evento == XMLStreamReader.START_ELEMENT && r.getLocalName().equals("si")) {
                actual = new StringBuilder();
            } else if (evento == XMLStreamReader.START_ELEMENT && r.getLocalName().equals("t") && actual != null) {
                actual.append(r.getElementText());
            } else if (evento == XMLStreamReader.END_ELEMENT && r.getLocalName().equals("si") && actual != null) {
                textos.add(actual.toString());
                actual = null;
            }
        }
        return textos;
    }

    private static List<List<String>> filasDeHoja(byte[] xml, List<String> compartidas) throws XMLStreamException {
        TreeMap<Integer, TreeMap<Integer, String>> filas = new TreeMap<>();
        XMLStreamReader r = lector(xml);
        int fila = 0;
        String referencia = null;
        String tipo = null;
        StringBuilder valor = null;
        while (r.hasNext()) {
            int evento = r.next();
            if (evento == XMLStreamReader.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "row" -> {
                        String numero = r.getAttributeValue(null, "r");
                        fila = numero == null ? fila + 1 : Integer.parseInt(numero);
                    }
                    case "c" -> {
                        referencia = r.getAttributeValue(null, "r");
                        tipo = r.getAttributeValue(null, "t");
                        valor = new StringBuilder();
                    }
                    case "v", "t" -> {
                        if (valor != null) {
                            valor.append(r.getElementText());
                        }
                    }
                    default -> {
                    }
                }
            } else if (evento == XMLStreamReader.END_ELEMENT && r.getLocalName().equals("c") && valor != null) {
                String texto = celda(tipo, valor.toString(), compartidas);
                int columna = referencia == null ? filas.getOrDefault(fila, new TreeMap<>()).size() : indiceColumna(referencia);
                filas.computeIfAbsent(fila, k -> new TreeMap<>()).put(columna, texto);
                valor = null;
            }
        }
        List<List<String>> tabla = new ArrayList<>();
        for (TreeMap<Integer, String> celdas : filas.values()) {
            List<String> valores = new ArrayList<>();
            int ultima = celdas.isEmpty() ? -1 : celdas.lastKey();
            for (int c = 0; c <= ultima; c++) {
                valores.add(celdas.getOrDefault(c, ""));
            }
            tabla.add(valores);
        }
        return tabla;
    }

    private static String celda(String tipo, String valor, List<String> compartidas) {
        if ("s".equals(tipo)) {
            int indice = Integer.parseInt(valor.trim());
            if (indice < 0 || indice >= compartidas.size()) {
                throw new DomainValidationException("El .xlsx apunta a un texto compartido que no existe");
            }
            return compartidas.get(indice);
        }
        if (tipo == null || "n".equals(tipo)) {
            // Codes typed as numbers come back as 20231001 or 2.0231001E7: write them as the digits typed.
            try {
                return new BigDecimal(valor.trim()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException notANumber) {
                return valor;
            }
        }
        return valor;
    }

    /** Zero-based column of a cell reference such as {@code C7} or {@code AB12}. */
    private static int indiceColumna(String referencia) {
        int columna = 0;
        for (char c : referencia.toCharArray()) {
            if (!Character.isLetter(c)) {
                break;
            }
            columna = columna * 26 + (Character.toUpperCase(c) - 'A' + 1);
        }
        return columna - 1;
    }
}
