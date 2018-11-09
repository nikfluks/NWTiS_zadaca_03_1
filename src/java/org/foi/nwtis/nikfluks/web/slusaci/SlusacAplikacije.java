package org.foi.nwtis.nikfluks.web.slusaci;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;
import org.foi.nwtis.nikfluks.web.dretve.PreuzmiMeteoPodatke;

/**
 * Klasa je web slušač i nakon što se pokrene aplikacija, ona starta dretvu PreuzmiMeteoPodatke
 *
 * @author Nikola
 * @version 1
 */
@WebListener
public class SlusacAplikacije implements ServletContextListener {

    private static ServletContext servletContext;
    private PreuzmiMeteoPodatke preuzmiMeteoPodatke;

    /**
     * Pokreće se kod pokretanja aplikacija
     *
     * @param sce ServletContextEvent
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        servletContext = sce.getServletContext();

        String datoteka = servletContext.getInitParameter("konfiguracija");
        String putanja = servletContext.getRealPath("/WEB-INF") + java.io.File.separator;
        BP_Konfiguracija bpk = new BP_Konfiguracija(putanja + datoteka);

        servletContext.setAttribute("BP_Konfig", bpk);
        preuzmiMeteoPodatke = new PreuzmiMeteoPodatke();
        preuzmiMeteoPodatke.start();
    }

    /**
     * Pokreće se kod brisanja (undeploy) aplikacije
     *
     * @param sce ServletContextEvent
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        servletContext = sce.getServletContext();
        servletContext.removeAttribute("BP_Konfig");
        preuzmiMeteoPodatke.interrupt();
    }

    /**
     * getter za Servlet Context
     *
     * @return Servlet Context
     */
    public static ServletContext getServletContext() {
        return servletContext;
    }
}
