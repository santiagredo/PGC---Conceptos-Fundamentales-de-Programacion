import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class main { 

    private static final String DELIMITER = ";";
    private static final String PRODUCTS_FILE = "productos.csv";
    private static final String SALESMEN_FILE = "vendedores.csv";
    private static final String REPORT_SALESMEN = "reporte_vendedores.csv";
    private static final String REPORT_PRODUCTS = "reporte_productos.csv";

    public static void main(String[] args) {
        try {
            System.out.println("Iniciando procesamiento de archivos de ventas...");

            // 1. Cargar datos de referencia desde productos.csv y vendedores.csv
            Map<Long, Double> productPrices = loadProductPrices();
            Map<Long, String> productNames = loadProductNames();
            Map<Long, String> salesmenNames = loadSalesmenNames();

            if (productPrices.isEmpty() || salesmenNames.isEmpty()) {
                System.err.println("Error: No se encontraron los archivos base. Ejecute primero GenerateInfoFiles.");
                return;
            }

            Map<Long, Double> salesPerSalesman = new HashMap<>();
            Map<Long, Integer> quantityPerProduct = new HashMap<>();

            // 2. Buscar dinámicamente los archivos de ventas generados por GenerateInfoFiles (.txt y .csv)
            File currentFolder = new File(".");
            File[] salesFiles = currentFolder.listFiles((dir, name) -> 
                name.startsWith("ventas_") && (name.endsWith(".txt") || name.endsWith(".csv"))
            );

            int totalArchivosProcesados = 0;
            int registrosIncoherentesIgnorados = 0;

            if (salesFiles != null) {
                for (File salesFile : salesFiles) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(salesFile))) {
                        String header = reader.readLine();

                        // EXTRA: Control de archivos vacíos o sin encabezado
                        if (header == null || header.trim().isEmpty()) {
                            continue;
                        }

                        String[] headerParts = header.split(DELIMITER);
                        if (headerParts.length < 2) {
                            continue;
                        }

                        long salesmanId;
                        try {
                            salesmanId = Long.parseLong(headerParts[1].trim());
                        } catch (NumberFormatException e) {
                            continue; // ID de vendedor mal formado
                        }

                        double totalSalesForThisFile = 0;
                        String line;

                        while ((line = reader.readLine()) != null) {
                            if (line.trim().isEmpty()) continue;

                            String[] data = line.split(DELIMITER);
                            if (data.length < 2) {
                                registrosIncoherentesIgnorados++;
                                continue;
                            }

                            try {
                                long productId = Long.parseLong(data[0].trim());
                                int quantity = Integer.parseInt(data[1].trim());

                                // EXTRA: Validar incoherencias (cantidades <= 0 o productos inexistentes)
                                if (quantity <= 0 || !productPrices.containsKey(productId)) {
                                    registrosIncoherentesIgnorados++;
                                    continue;
                                }

                                double price = productPrices.get(productId);
                                totalSalesForThisFile += (price * quantity);

                                // Acumular cantidad vendida del producto a nivel global
                                quantityPerProduct.put(productId, quantityPerProduct.getOrDefault(productId, 0) + quantity);

                            } catch (NumberFormatException e) {
                                registrosIncoherentesIgnorados++;
                            }
                        }

                        // EXTRA: Soporta múltiples archivos acumulando al mismo vendedor
                        salesPerSalesman.put(salesmanId, salesPerSalesman.getOrDefault(salesmanId, 0.0) + totalSalesForThisFile);
                        totalArchivosProcesados++;

                    } catch (IOException e) {
                        System.err.println("Error leyendo el archivo: " + salesFile.getName());
                    }
                }
            }

            // 3. Generar Reporte 1: Vendedores ordenados de mayor a menor recaudación
            List<Map.Entry<Long, Double>> sortedSalesmen = new ArrayList<>(salesPerSalesman.entrySet());
            sortedSalesmen.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(REPORT_SALESMEN))) {
                for (Map.Entry<Long, Double> entry : sortedSalesmen) {
                    String name = salesmenNames.getOrDefault(entry.getKey(), "Vendedor Desconocido (ID: " + entry.getKey() + ")");
                    double totalFormatted = Math.round(entry.getValue() * 100.0) / 100.0;
                    writer.write(name + DELIMITER + totalFormatted);
                    writer.newLine();
                }
            }

            // 4. Generar Reporte 2: Productos ordenados de mayor a menor cantidad vendida
           List<Map.Entry<Long, Integer>> sortedProducts = new ArrayList<>(quantityPerProduct.entrySet());
                sortedProducts.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(REPORT_PRODUCTS))) {
                for (Map.Entry<Long, Integer> entry : sortedProducts) {
                    long prodId = entry.getKey();
                    String name = productNames.getOrDefault(prodId, "Producto " + prodId);
                    int quantity = entry.getValue();

                    // Formato solicitado: NombreProducto;Cantidad
                    writer.write(name + DELIMITER + quantity);
                    writer.newLine();
                 }
            }

            System.out.println("=========================================");
            System.out.println("Procesamiento completado exitosamente.");
            System.out.println("Archivos procesados: " + totalArchivosProcesados);
            System.out.println("Registros erróneos descartados: " + registrosIncoherentesIgnorados);
            System.out.println("Reportes generados: " + REPORT_SALESMEN + " y " + REPORT_PRODUCTS);
            System.out.println("=========================================");

        } catch (IOException e) {
            System.err.println("Error crítico durante la ejecución: " + e.getMessage());
        }
    }

    private static Map<Long, Double> loadProductPrices() throws IOException {
        Map<Long, Double> map = new HashMap<>();
        File file = new File(PRODUCTS_FILE);
        if (!file.exists()) return map;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(DELIMITER);
                if (parts.length >= 3) {
                    try {
                        long id = Long.parseLong(parts[0].trim());
                        double price = Double.parseDouble(parts[2].trim());
                        if (price >= 0) map.put(id, price);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return map;
    }

    private static Map<Long, String> loadProductNames() throws IOException {
        Map<Long, String> map = new HashMap<>();
        File file = new File(PRODUCTS_FILE);
        if (!file.exists()) return map;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(DELIMITER);
                if (parts.length >= 2) {
                    try {
                        long id = Long.parseLong(parts[0].trim());
                        map.put(id, parts[1].trim());
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return map;
    }

    private static Map<Long, String> loadSalesmenNames() throws IOException {
        Map<Long, String> map = new HashMap<>();
        File file = new File(SALESMEN_FILE);
        if (!file.exists()) return map;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(DELIMITER);
                if (parts.length >= 4) {
                    try {
                        long doc = Long.parseLong(parts[1].trim());
                        String fullName = parts[2].trim() + " " + parts[3].trim();
                        map.put(doc, fullName);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return map;
    }
}