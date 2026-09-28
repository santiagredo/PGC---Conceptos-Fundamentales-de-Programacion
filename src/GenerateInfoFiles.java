import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GenerateInfoFiles {

    private static final String DELIMITER = ";";
    private static final String PRODUCTS_FILE = "productos.csv";
    private static final String SALESMEN_FILE = "vendedores.csv";
    private static final Random RANDOM = new Random();

    private static final List<String> FIRST_NAMES = Arrays.asList(
            "Carlos", "Ana", "Luis", "Maria", "Juan", "Laura", "Pedro", "Sofia");
    private static final List<String> LAST_NAMES = Arrays.asList(
            "Gomez", "Rodriguez", "Perez", "Martinez", "Garcia", "Lopez", "Torres");
    private static final List<String> DOC_TYPES = Arrays.asList("CC", "CE", "NIT");

    public static void main(String[] args) {
        try {
            int productsCount = 10;
            int salesmenCount = 5;
            int maxSalesPerFile = 15;

            createProductsFile(productsCount);
            List<SalesmanData> salesmen = createSalesManInfoFile(salesmenCount);

            for (SalesmanData sm : salesmen) {
                createSalesMenFile(maxSalesPerFile, "ventas_" + sm.getId() + ".txt", sm.getDocType(), sm.getId());
            }

            System.out.println("Generación de archivos completada exitosamente.");
        } catch (IOException e) {
            System.err.println("Error generando los archivos: " + e.getMessage());
        }
    }

    public static void createSalesMenFile(int randomSalesCount, String name, long id) throws IOException {
        createSalesMenFile(randomSalesCount, name, DOC_TYPES.get(RANDOM.nextInt(DOC_TYPES.size())), id);
    }

    private static void createSalesMenFile(int randomSalesCount, String name, String docType, long id)
            throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(name)))) {
            writer.write(docType + DELIMITER + id);
            writer.newLine();

            int salesCount = RANDOM.nextInt(randomSalesCount) + 1;
            for (int i = 0; i < salesCount; i++) {
                writer.write((RANDOM.nextInt(10) + 1) + DELIMITER + (RANDOM.nextInt(20) + 1) + DELIMITER);
                writer.newLine();
            }
        }
    }

    public static void createProductsFile(int productsCount) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(PRODUCTS_FILE)))) {
            for (int i = 1; i <= productsCount; i++) {
                double unitPrice = Math.round((1000 + 99000 * RANDOM.nextDouble()) * 100.0) / 100.0;
                writer.write(i + DELIMITER + "Producto_" + i + DELIMITER + unitPrice);
                writer.newLine();
            }
        }
    }

    public static List<SalesmanData> createSalesManInfoFile(int salesmanCount) throws IOException {
        List<SalesmanData> salesmenList = new ArrayList<>();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(SALESMEN_FILE)))) {
            for (int i = 0; i < salesmanCount; i++) {
                String docType = DOC_TYPES.get(RANDOM.nextInt(DOC_TYPES.size()));
                long docNumber = 10000000L + RANDOM.nextInt(90000000);
                String firstName = FIRST_NAMES.get(RANDOM.nextInt(FIRST_NAMES.size()));
                String lastName = LAST_NAMES.get(RANDOM.nextInt(LAST_NAMES.size()));

                writer.write(docType + DELIMITER + docNumber + DELIMITER + firstName + DELIMITER + lastName);
                writer.newLine();

                salesmenList.add(new SalesmanData(docType, docNumber));
            }
        }
        return salesmenList;
    }

    private static class SalesmanData {
        private final String docType;
        private final long id;

        SalesmanData(String docType, long id) {
            this.docType = docType;
            this.id = id;
        }

        public String getDocType() {
            return docType;
        }

        public long getId() {
            return id;
        }
    }
}