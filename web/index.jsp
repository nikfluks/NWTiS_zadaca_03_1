<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <link rel="stylesheet" type="text/css" href="resources/css/osnovni.css">
        <title>Dodavanje parkirališta</title>
    </head>
    <body>
        <h1>Dodavanje parkirališta</h1>
        <form method="POST" 
              action="${pageContext.servletContext.contextPath}/DodajParkiraliste"
              class="indexForma">
            <table>
                <tr>
                    <td>Naziv i adresa: 
                        <input class="indexMargina" name="naziv" placeholder="Upiši naziv" value="${naziv}"/>
                    </td>
                    <td><input name="adresa" placeholder="Upiši adresu" value="${adresa}"/></td>
                    <td><input class="indexMargina" type="submit" name="geolokacija" value="Geolokacija"/></td>
                </tr>
                <tr>
                    <td colspan="2">Geolokacija:
                        <input class="indexMarginaGeolokacija" name="lokacija" readonly="readonly" 
                               size="45" value="${geolokacija}"/>
                    </td>
                    <td><input class="indexMargina"  type="submit" name="spremi" value="Spremi"/></td>
                </tr>
                <tr>
                    <td colspan="2"></td>
                    <td><input class="indexMargina" type="submit" name="meteo" value="Meteo podaci"/></td>
                </tr>
            </table>
            <div class="upisUBazu">
                ${uspjesanUpis}<br>
            </div>
            <div>
                Temp: ${temp}<br/>
                Vlaga: ${vlaga}<br/>
                Tlak: ${tlak}<br/>
            </div>
        </form>
    </body>
</html>
