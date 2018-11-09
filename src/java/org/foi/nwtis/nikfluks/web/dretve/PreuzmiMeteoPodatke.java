package org.foi.nwtis.nikfluks.web.dretve;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;
import org.foi.nwtis.nikfluks.rest.klijenti.OWMKlijent;
import org.foi.nwtis.nikfluks.web.podaci.Lokacija;
import org.foi.nwtis.nikfluks.web.podaci.MeteoPodaci;
import org.foi.nwtis.nikfluks.web.podaci.Parkiraliste;
import org.foi.nwtis.nikfluks.web.slusaci.SlusacAplikacije;

/**
 * Klasa je dretva koja u određenom intervalu dohvaća meteo podatke sa servisa i zapisuje ih u bazu podataka.
 *
 * @author Nikola
 * @version 1
 */
public class PreuzmiMeteoPodatke extends Thread {

    boolean radi = true;
    String url;
    String korIme;
    String lozinka;
    String uprProgram;
    Connection con;
    int intervalOsvjezavanja;
    String apikey;
    OWMKlijent owmk;
    int broj = 1;

    /**
     * Prekida rad dreteve
     */
    @Override
    public void interrupt() {
        radi = false;
        super.interrupt();
    }

    /**
     * Tijekom rada dretva dohvaća sva parkirališta u određenom intervalu te iz zatim zapisuje u bazu podataka
     */
    @Override
    public void run() {
        while (radi) {
            try {
                System.out.println("upisujem " + broj + ". put");

                con = DriverManager.getConnection(url, korIme, lozinka);
                List<Parkiraliste> svaParkiralista = dohvatiParkiralista();
                if (svaParkiralista != null) {
                    for (Parkiraliste p : svaParkiralista) {
                        Lokacija lok = p.getGeolokacija();
                        MeteoPodaci mp = owmk.getRealTimeWeather(lok.getLatitude(), lok.getLongitude());
                        upisiMeteoPodatke(mp, p);
                    }
                }
                broj++;
                con.close();
                sleep(intervalOsvjezavanja);
            } catch (InterruptedException | SQLException ex) {
                Logger.getLogger(PreuzmiMeteoPodatke.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                Logger.getLogger(PreuzmiMeteoPodatke.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Pokreće dretvu
     */
    @Override
    public synchronized void start() {
        if (dohvatiPodatkeIzKonfiguracije()) {
            super.start();
        }
    }

    /**
     * Dohvaća podatke iz konfiguracijske datoteke
     *
     * @return true ako su uspješno dohvaćeni, false inače
     */
    public boolean dohvatiPodatkeIzKonfiguracije() {
        try {
            BP_Konfiguracija bpk = (BP_Konfiguracija) SlusacAplikacije.getServletContext().getAttribute("BP_Konfig");

            url = bpk.getServerDatabase() + bpk.getUserDatabase();
            korIme = bpk.getUserUsername();
            lozinka = bpk.getUserPassword();
            uprProgram = bpk.getDriverDatabase();
            intervalOsvjezavanja = Integer.parseInt(bpk.getIntervalDretveZaMeteoPodatke()) * 1000;
            apikey = bpk.getApiKey();
            owmk = new OWMKlijent(apikey);
            Class.forName(uprProgram);

            return true;
        } catch (ClassNotFoundException ex) {
            System.err.println("Greška kod dohvatiPodatkeIzKonfiguracije");
            return false;
        }
    }

    /**
     * Dohvaća sva parkirališta iz baze podataka.
     *
     * @return lista parkirališta ili null u slučaju greške
     */
    private List<Parkiraliste> dohvatiParkiralista() {
        List<Parkiraliste> svaParkiralista = new ArrayList<>();
        String upit = "SELECT * FROM PARKIRALISTA";

        try {
            PreparedStatement stat = con.prepareStatement(upit);
            ResultSet rs = stat.executeQuery();

            while (rs.next()) {
                Lokacija lokacija = new Lokacija(rs.getString("latitude"), rs.getString("longitude"));
                svaParkiralista.add(new Parkiraliste(rs.getInt("id"), rs.getString("naziv"), rs.getString("adresa"), lokacija));
            }
            rs.close();
            stat.close();
            return svaParkiralista;
        } catch (SQLException ex) {
            System.err.println("Greška pri dohvaćanju parkirališta");
            return null;
        }
    }

    /**
     * Upisuje meteo podatke i podatke o parkiralištima u bazu podataka
     *
     * @param mp meteo podaci
     * @param p parkiralište
     * @return true ako su podaci uspješno upisani, false inače
     */
    private boolean upisiMeteoPodatke(MeteoPodaci mp, Parkiraliste p) {
        String upit = "INSERT INTO meteo "
                + "(id, adresastanice, latitude, longitude, vrijeme, vrijemeopis, temp, tempmin, tempmax, vlaga, tlak, vjetar, vjetarsmjer) "
                + " VALUES (" + p.getId() + ", '" + p.getAdresa() + "', " + p.getGeolokacija().getLatitude() + ", " + p.getGeolokacija().getLongitude()
                + ", '" + mp.getWeatherNumber() + "', '" + mp.getWeatherValue() + "', " + mp.getTemperatureValue()
                + ", " + mp.getTemperatureMin() + ", " + mp.getTemperatureMax() + ", " + mp.getHumidityValue() + ", " + mp.getPressureValue()
                + ", " + mp.getWindSpeedValue() + ", " + mp.getWindDirectionValue() + ")";

        try {
            PreparedStatement stat = con.prepareStatement(upit);
            stat.execute();
            stat.close();
            System.out.println("upisani meteo podaci koji su zadnji put osvjezeni: " + mp.getLastUpdate());
            return true;
        } catch (SQLException ex) {
            System.err.println("Greška kod upisa meteo podataka!");
            return false;
        }
    }
}
