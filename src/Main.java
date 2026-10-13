import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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

// Lee los archivos de entrada, genera los reportes CSV y guarda respaldos serializados (.dat)
public class Main {

    private static final String DELIMITER = ";";
    private static final String PRODUCTS_FILE = "productos.csv";
    private static final String SALESMEN_FILE = "vendedores.csv";
    private static final String SALESMEN_REPORT = "reporte_vendedores.csv";
    private static final String PRODUCTS_REPORT = "reporte_productos.csv";

    // Archivos para guardar los datos serializados
    private static final String PRODUCTS_SER_FILE = "productos.dat";
    private static final String SALESMEN_SER_FILE = "vendedores.dat";

    // Clase Product serializable
    private static class Product implements Serializable {
        private static final long serialVersionUID = 1L;

        final String name;
        final double price;
        long sold;

        Product(String name, double price) {
            this.name = name;
            this.price = price;
        }
    }

    // Clase Salesman serializable
    private static class Salesman implements Serializable {
        private static final long serialVersionUID = 1L;

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

            // Generación de reportes CSV
            writeSalesmenReport(salesmen);
            writeProductsReport(products);

            // Respaldo de datos mediante serialización binaria
            saveSerializedData(products, PRODUCTS_SER_FILE);
            saveSerializedData(salesmen, SALESMEN_SER_FILE);

            System.out.println("Reportes y archivos serializados generados exitosamente.");

        } catch (IOException e) {
            System.err.println("Error procesando los archivos: " + e.getMessage());
        }
    }

    // Carga los productos válidos desde CSV; descarta líneas con formato o precio inválido.
    private static Map<Integer, Product> loadProducts() throws IOException {
        Map<Integer, Product> products = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(PRODUCTS_FILE), StandardCharsets.UTF_8)) {
            String cleanLine = line.replace("\uFEFF", "").trim();
            if (cleanLine.isEmpty())
                continue;
            try {
                String[] fields = cleanLine.split(DELIMITER);
                if (fields.length != 3)
                    throw new IllegalArgumentException();

                int id = Integer.parseInt(fields[0].trim());
                String name = fields[1].trim();
                double price = Double.parseDouble(fields[2].trim());

                if (price < 0)
                    throw new IllegalArgumentException();

                products.put(id, new Product(name, price));
            } catch (RuntimeException e) {
                warn(PRODUCTS_FILE, "línea inválida: " + line);
            }
        }
        return products;
    }

    // Carga los vendedores desde CSV indexados por "tipoDocumento;númeroDocumento"
    private static Map<String, Salesman> loadSalesmen() throws IOException {
        Map<String, Salesman> salesmen = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(SALESMEN_FILE), StandardCharsets.UTF_8)) {
            String cleanLine = line.replace("\uFEFF", "").trim();
            if (cleanLine.isEmpty())
                continue;
            String[] fields = cleanLine.split(DELIMITER);
            if (fields.length != 4) {
                warn(SALESMEN_FILE, "línea inválida: " + line);
                continue;
            }
            salesmen.put(fields[0].trim() + DELIMITER + fields[1].trim(),
                    new Salesman(fields[2].trim() + " " + fields[3].trim()));
        }
        return salesmen;
    }

    // Acumula las ventas de un archivo de texto; varios archivos del mismo vendedor se suman.
    private static void processSalesFile(Path file, Map<String, Salesman> salesmen,
                                         Map<Integer, Product> products) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return;

        String headerLine = lines.get(0).replace("\uFEFF", "").trim();
        String[] header = headerLine.split(DELIMITER);

        Salesman salesman = header.length == 2
                ? salesmen.get(header[0].trim() + DELIMITER + header[1].trim())
                : null;

        if (salesman == null) {
            warn(file.getFileName().toString(), "vendedor inválido o no registrado");
            return;
        }

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty())
                continue;
            try {
                String[] fields = line.split(DELIMITER);
                if (fields.length < 2)
                    throw new IllegalArgumentException();

                Product product = products.get(Integer.parseInt(fields[0].trim()));
                int quantity = Integer.parseInt(fields[1].trim());

                if (product == null || quantity <= 0)
                    throw new IllegalArgumentException();

                salesman.revenue += product.price * quantity;
                product.sold += quantity;
            } catch (RuntimeException e) {
                warn(file.getFileName().toString(), "línea " + (i + 1) + " inválida: " + lines.get(i));
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

    // Escribe "nombre;cantidadVendida" de los productos vendidos
    private static void writeProductsReport(Map<Integer, Product> products) throws IOException {
        List<Product> sold = new ArrayList<>();
        for (Product p : products.values()) {
            if (p.sold > 0)
                sold.add(p);
        }
        sold.sort((a, b) -> Long.compare(b.sold, a.sold));

        List<String> lines = new ArrayList<>();
        for (Product p : sold) {
            lines.add(p.name + DELIMITER + p.sold);
        }
        Files.write(Paths.get(PRODUCTS_REPORT), lines, StandardCharsets.UTF_8);
    }

    // Guardar objetos serializados en disco (.dat)
    private static void saveSerializedData(Object object, String fileName) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileName))) {
            oos.writeObject(object);
            System.out.println("Archivo serializado guardado: " + fileName);
        } catch (IOException e) {
            System.err.println("Error serializando datos en " + fileName + ": " + e.getMessage());
        }
    }

    // Método utilitario opcional: Cargar objetos deserializados desde disco (.dat)
    @SuppressWarnings("unchecked")
    private static <T> T loadSerializedData(String fileName) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName))) {
            return (T) ois.readObject();
        }
    }

    private static void warn(String source, String message) {
        System.err.println("[ADVERTENCIA] " + source + ": " + message);
    }
}