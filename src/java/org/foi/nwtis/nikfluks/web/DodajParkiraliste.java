package org.foi.nwtis.nikfluks.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;
import org.foi.nwtis.nikfluks.rest.klijenti.GMKlijent;
import org.foi.nwtis.nikfluks.rest.klijenti.OWMKlijent;
import org.foi.nwtis.nikfluks.web.podaci.Lokacija;
import org.foi.nwtis.nikfluks.web.podaci.MeteoPodaci;
import org.foi.nwtis.nikfluks.web.slusaci.SlusacAplikacije;

/**
 * Klasa je web servlet te služi za unos podataka o parkiralište te dohvaćanje geolokacije i osnovnih meteo podataka o određenom
 * parkiralištu
 *
 * @author Nikola
 * @version 1
 */
@WebServlet(name = "DodajParkiraliste", urlPatterns = {"/DodajParkiraliste"})
public class DodajParkiraliste extends HttpServlet {

    String url;
    String korIme;
    String lozinka;
    String apikey;
    String gmapikey;
    String uprProgram;
    Connection con;
    String naziv;
    String adresa;
    Lokacija lok;
    MeteoPodaci mp;
    String geolokacija;
    String spremi;
    String meteo;
    GMKlijent gmk;
    OWMKlijent owmk;

    /**
     * Dohvaća podatke iz konfiguracijske datoteke
     *
     * @return true ako su uspješno dohvaćeni, false inače
     */
    public boolean dohvatiPodatkeIzKonfiguracije() {
        try {
            BP_Konfiguracija bpk = (BP_Konfiguracija) SlusacAplikacije.getServletContext().getAttribute("BP_Konfig");

            url = bpk.getServerDatabase() + bpk.getUserDatabase();
            korIme = bpk.getUserDatabase();
            lozinka = bpk.getUserPassword();
            uprProgram = bpk.getDriverDatabase();
            apikey = bpk.getApiKey();
            owmk = new OWMKlijent(apikey);
            gmapikey = bpk.getGmApiKey();
            gmk = new GMKlijent(gmapikey);
            Class.forName(uprProgram);
            return true;
        } catch (ClassNotFoundException ex) {
            System.err.println("Greška kod dohvatiPodatkeIzKonfiguracije");
            return false;
        }
    }

    /**
     * Obrađuje zahtjeve korisnika
     *
     * @param request zahtjev korisnika
     * @param response odgovor prema korisniku
     * @throws ServletException iznimka servleta
     * @throws IOException iznimka IO
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.displayName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.displayName());

        if (!dohvatiPodatkeIzKonfiguracije()) {
            return;
        }

        geolokacija = request.getParameter("geolokacija");
        spremi = request.getParameter("spremi");
        meteo = request.getParameter("meteo");
        naziv = request.getParameter("naziv");
        adresa = request.getParameter("adresa");

        if (geolokacija != null) {
            odrediGeolokaciju(request, response);
        } else if (spremi != null) {
            odrediGeolokaciju(request, response);
            spremiUBazu(request);
        } else if (meteo != null) {
            odrediGeolokaciju(request, response);
            dohvatiMeteoPodatke(request);
        }
        postaviAtributeIProslijedi(request, response);
    }

    /**
     * Ispisuje poruku pogreške na ekran korisnika.
     *
     * @param request zahtjev korisnika
     * @param response odgovor prema korisniku
     * @param greska greška koja se ispisuje
     * @throws IOException iznimka IO
     */
    private void ispisi(HttpServletRequest request, HttpServletResponse response, String greska) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Servlet Dodaj parkiralište</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>Servlet - Dodaj parkiralište, putanja: " + request.getContextPath() + "</h1>");
            out.println("<h3>Greška: " + greska + "</h3>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    /**
     * Dohvaća geolokaciju temeljem adrese
     *
     * @param request zahtjev korisnika
     * @param response odgovor prema korisniku
     * @throws IOException iznimka IO
     */
    private void odrediGeolokaciju(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (naziv.isEmpty() || adresa.isEmpty()) {
            ispisi(request, response, "Nisu unijeti svi podaci!");
        } else {
            try {
                lok = gmk.getGeoLocation(adresa);
            } catch (Exception e) {
                ispisi(request, response, "Greška pri dohvaćanju geolokacije!");
            }
        }
    }

    /**
     * Sprema podatke o parkiralištu bazu podataka
     *
     * @param request zahtjev korisnika
     */
    private void spremiUBazu(HttpServletRequest request) {
        String upit = "INSERT INTO parkiralista (naziv, adresa, latitude, longitude)"
                + " VALUES ('" + naziv + "','" + adresa + "'," + lok.getLatitude() + "," + lok.getLongitude() + ")";
        String uspjesanUpis = "";

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            stat.execute();
            stat.close();
            con.close();
            uspjesanUpis = "Uspješno upisano parkiralište u bazu!";
        } catch (SQLException ex) {
            uspjesanUpis = "Greška kod upisa parkirališta u bazu";
        }
        request.setAttribute("uspjesanUpis", uspjesanUpis);
    }

    /**
     * Dohvaća meteo podatke temeljem geolokacije
     *
     * @param request zahtjev korisnika
     */
    private void dohvatiMeteoPodatke(HttpServletRequest request) {
        mp = owmk.getRealTimeWeather(lok.getLatitude(), lok.getLongitude());

        request.setAttribute("temp", mp.getTemperatureValue() + " " + mp.getTemperatureUnit());
        request.setAttribute("vlaga", mp.getHumidityValue() + " " + mp.getHumidityUnit());
        request.setAttribute("tlak", mp.getPressureValue() + " " + mp.getPressureUnit());
    }

    /**
     * Proslijeđuje obrađene podatke korisniku
     *
     * @param request zahtjev korisnika
     * @param response odgovor prema korisniku
     * @throws ServletException servlet iznimka
     * @throws IOException izminka IO
     */
    private void postaviAtributeIProslijedi(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (lok != null) {
            request.setAttribute("geolokacija", lok.getLatitude() + "," + lok.getLongitude());
            lok = null;
        }
        request.setAttribute("naziv", naziv);
        request.setAttribute("adresa", adresa);
        RequestDispatcher dispatcher = request.getRequestDispatcher("index.jsp");
        dispatcher.forward(request, response);
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
