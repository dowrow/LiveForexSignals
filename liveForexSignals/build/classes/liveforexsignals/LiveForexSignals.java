/*
 * LiveForexSignals: Scrapper para live-forex-signals.com
 * @author dowrow 
 */
package liveforexsignals;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LiveForexSignals {

    private static final String URL = "http://live-forex-signals.com/index.php";
    private static final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    private static WebClient cliente = new WebClient(BrowserVersion.CHROME);
    private static Long intervalo;
    private static String fichero;
    private static Date ultimaActualizacion = (new GregorianCalendar()).getTime();
    // Listas reutilizables (para reducir GC overhead)
    // DOM
    private static ArrayList<HtmlElement> signalDivs;
    private static ArrayList<HtmlElement> dateBolds;
    private static ArrayList<HtmlElement> statusSpans;
    private static ArrayList<HtmlElement> takeStops;
    private static ArrayList<HtmlElement> priceFonts;
    // Datos
    private static ArrayList<String> nombres = new ArrayList<>();
    private static ArrayList<String> tipos = new ArrayList<>();
    private static ArrayList<String> desdes = new ArrayList<>();
    private static ArrayList<String> hastas = new ArrayList<>();
    private static ArrayList<String> takeprofits = new ArrayList<>();
    private static ArrayList<String> stoplosses = new ArrayList<>();
    private static ArrayList<String> precios = new ArrayList<>();
    private static FileWriter writer;

    public static void main(String[] args) {
        try {
            
            // Ocultar warnings .jar
            java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(java.util.logging.Level.OFF);
            java.util.logging.Logger.getLogger("org.apache").setLevel(java.util.logging.Level.OFF);
            
            if (args.length != 2) {
                System.out.println("Número incorrecto de parámetros");
                System.out.println("Modo de uso:");
                System.out.println("\tjava -jar liveforexsignals.jar <intervalo_en_segundos> <nombre_fichero_csv>");
                return;
            }

            intervalo = Long.parseLong(args[0]);
            fichero = args[1].trim();
            cliente.getOptions().setThrowExceptionOnScriptError(false);
            
            ses.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    LiveForexSignals.run();
                }
            }, 0, intervalo, TimeUnit.SECONDS);
            
        } catch (SecurityException | NumberFormatException e) {
            System.out.println("ERROR - El intervalo debe ser mayor que 0");
        }

    }

    private static void generarCSV(String fichero, HtmlPage pagina) throws IOException {

        try {

            signalDivs = (ArrayList<HtmlElement>) pagina.getByXPath("//td[@class='signal sell' or @class='signal buy']");
            dateBolds = (ArrayList<HtmlElement>) pagina.getByXPath("//td[@class='signal sell' or @class='signal buy']/div/div[@class='info']/b");
            statusSpans = (ArrayList<HtmlElement>) pagina.getByXPath("//td[@class='signal sell' or @class='signal buy']/div/div[@class='info']/div[@class='status']/span");
            takeStops = (ArrayList<HtmlElement>) pagina.getByXPath("//td[@class='signal sell' or @class='signal buy']/div/div[@class='info']/font[@size='+2']");
            priceFonts = (ArrayList<HtmlElement>) pagina.getByXPath("//td[@class='signal sell' or @class='signal buy']/div/div[@class='info']/span[@class='buytext' or @class='selltext']/font[@size='+2']");

            // Saca nombres (Ej: "USDCAD")
            for (HtmlElement div : signalDivs) {
                nombres.add(div.getAttribute("id"));
            }

            // Saca tipos
            for (HtmlElement span : statusSpans) {
                tipos.add(span.asText());
            }

            // Saca from y till
            for (int i = 0; i < dateBolds.size(); i += 2) {
                desdes.add(dateBolds.get(i).asText());
                hastas.add(dateBolds.get(i + 1).asText());
            }

            // Saca takeprofits y stoplosses
            for (int i = 0; i < dateBolds.size(); i += 2) {
                takeprofits.add(takeStops.get(i).asText());
                stoplosses.add(takeStops.get(i + 1).asText());
            }

            // Saca precios
            for (HtmlElement font : priceFonts) {
                precios.add(font.asText());
            }

            // Generar CSV
            writer = new FileWriter(fichero);

            writer.write("Nombre,Tipo,Desde,Hasta,Takeprofit,Stoploss,Precio\r\n");

            for (int i = 0; i < nombres.size(); i++) {
                writer.append(nombres.get(i) + ",");
                writer.append(tipos.get(i) + ",");
                writer.append(desdes.get(i) + ",");
                writer.append(hastas.get(i) + ",");
                writer.append(takeprofits.get(i) + ",");
                writer.append(stoplosses.get(i) + ",");
                writer.append(precios.get(i));
                writer.append("\r\n");
            }

            writer.flush();
            writer.close();

            System.out.println("\t> El fichero ha sido actualizado");
            ultimaActualizacion = (new GregorianCalendar()).getTime();
           
        } catch (IOException e) {
            System.out.println("ERROR - No se pudo acceder al fichero");
        } catch (Exception e) {
            System.out.println("ERROR - La página no tiene el formato esperado. No se pudo extraer la información");
        } finally {
            signalDivs = null;
            dateBolds = null;
            statusSpans = null;
            takeStops = null;
            priceFonts = null;

            nombres.clear();
            tipos.clear();
            desdes.clear();
            hastas.clear();
            takeprofits.clear();
            stoplosses.clear();
            precios.clear();

            writer = null;
        }

    }

    private static void run() {
        HtmlPage pagina;
        
        try {
            
            System.out.println((new GregorianCalendar()).getTime());
            System.out.println("\t> Conectando...");
            cliente = new WebClient(BrowserVersion.CHROME);
            pagina = cliente.getPage(URL);

            if (pagina.getTitleText().equals("Live-Forex-signals.com - captcha")) {
                System.out.println("\t> Ha saltado el captcha");
                System.out.println("\t> Última actualización correcta: " + ultimaActualizacion);
            } else {
                System.out.println("\t> Procesando datos...");
                generarCSV(fichero, pagina);       
            }
            
        } catch (IOException | FailingHttpStatusCodeException e) {
            System.out.println("ERROR - No se pudo acceder a la web");
            System.out.println("ERROR - Compruebe que la aplicación tenga permiso para conectarse");
        } finally {
            cliente.closeAllWindows();
            cliente.getCache().clear();
            cliente = null;
            pagina = null;
            System.gc();
        }
    }
}
