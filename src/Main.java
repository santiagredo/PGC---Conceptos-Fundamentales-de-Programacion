import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Lee los archivos de entrada y genera los reportes de vendedores y de
// productos.
public class Main {

    private static final String DELIMITER = ";";
    private static final String PRODUCTS_FILE = "productos.csv";
    private static final String SALESMEN_FILE = "vendedores.csv";
    private static final String SALESMEN_REPORT = "reporte_vendedores.csv";
    private static final String PRODUCTS_REPORT = "reporte_productos.csv";

    private static class Product {
        final String name;
        final double price;
        long sold;

        Product(String name, double price) {
            this.name = name;
            this.price = price;
        }
    }

    private static class Salesman {
        final String fullName;
        double revenue;

        Salesman(String fullName) {
            this.fullName = fullName;
        }
    }

    public static void main(String[] args) {
        try {
            Map<Integer, Product> products = loadProducts();
            Map<String, Salesman> salesmen = loadSalesmen();

            try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("."), "ventas_*.txt")) {
                for (Path file : files) {
                    processSalesFile(file, salesmen, products);
                }
            }

            writeSalesmenReport(salesmen);
            writeProductsReport(products);
            System.out.println("Reportes generados exitosamente.");
        } catch (IOException e) {
            System.err.println("Error procesando los archivos: " + e.getMessage());
        }
    }

    // Carga los productos válidos indexados por ID; descarta líneas con formato o
    // precio inválido.
    private static Map<Integer, Product> loadProducts() throws IOException {
        Map<Integer, Product> products = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(PRODUCTS_FILE), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty())
                continue;
            try {
                String[] fields = line.split(DELIMITER);
                double price = Double.parseDouble(fields[2].trim());
                if (fields.length != 3 || price < 0)
                    throw new IllegalArgumentException();
                products.put(Integer.parseInt(fields[0].trim()), new Product(fields[1].trim(), price));
            } catch (RuntimeException e) {
                warn(PRODUCTS_FILE, "línea inválida: " + line);
            }
        }
        return products;
    }

    // Carga los vendedores indexados por "tipoDocumento;númeroDocumento"
    private static Map<String, Salesman> loadSalesmen() throws IOException {
        Map<String, Salesman> salesmen = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(SALESMEN_FILE), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty())
                continue;
            String[] fields = line.split(DELIMITER);
            if (fields.length != 4) {
                warn(SALESMEN_FILE, "línea inválida: " + line);
                continue;
            }
            salesmen.put(fields[0].trim() + DELIMITER + fields[1].trim(),
                    new Salesman(fields[2].trim() + " " + fields[3].trim()));
        }
        return salesmen;
    }

    // Acumula las ventas de un archivo; varios archivos del mismo vendedor se
    // suman.
    private static void processSalesFile(Path file, Map<String, Salesman> salesmen,
            Map<Integer, Product> products) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String[] header = lines.isEmpty() ? new String[0] : lines.get(0).split(DELIMITER);
        Salesman salesman = header.length == 2
                ? salesmen.get(header[0].trim() + DELIMITER + header[1].trim())
                : null;

        if (salesman == null) {
            warn(file.toString(), "vendedor inválido o no registrado");
            return;
        }

        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty())
                continue;
            try {
                String[] fields = lines.get(i).split(DELIMITER);
                Product product = products.get(Integer.parseInt(fields[0].trim()));
                int quantity = Integer.parseInt(fields[1].trim());
                if (product == null || quantity <= 0)
                    throw new IllegalArgumentException();
                salesman.revenue += product.price * quantity;
                product.sold += quantity;
            } catch (RuntimeException e) {
                warn(file.toString(), "línea " + (i + 1) + " inválida: " + lines.get(i));
            }
        }
    }

    // Escribe "nombre;recaudo" ordenado de mayor a menor recaudo
    private static void writeSalesmenReport(Map<String, Salesman> salesmen) throws IOException {
        List<Salesman> sorted = new ArrayList<>(salesmen.values());
        sorted.sort((a, b) -> Double.compare(b.revenue, a.revenue));

        List<String> lines = new ArrayList<>();
        for (Salesman s : sorted) {
            lines.add(s.fullName + DELIMITER + String.format(Locale.US, "%.2f", s.revenue));
        }
        Files.write(Paths.get(SALESMEN_REPORT), lines, StandardCharsets.UTF_8);
    }

    // Escribe "nombre;precio" de los productos vendidos, de mayor a menor cantidad
    // vendida.
    private static void writeProductsReport(Map<Integer, Product> products) throws IOException {
        List<Product> sold = new ArrayList<>();
        for (Product p : products.values()) {
            if (p.sold > 0)
                sold.add(p);
        }
        sold.sort((a, b) -> Long.compare(b.sold, a.sold));

        List<String> lines = new ArrayList<>();
        for (Product p : sold) {
            lines.add(p.name + DELIMITER + String.format(Locale.US, "%.2f", p.price));
        }
        Files.write(Paths.get(PRODUCTS_REPORT), lines, StandardCharsets.UTF_8);
    }

    private static void warn(String source, String message) {
        System.err.println("[ADVERTENCIA] " + source + ": " + message);
    }
}