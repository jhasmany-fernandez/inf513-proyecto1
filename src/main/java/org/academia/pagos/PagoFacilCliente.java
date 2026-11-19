package org.academia.pagos;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Cliente de Pago Fácil Bolivia: autenticación, QR y consulta de transacciones. */
public final class PagoFacilCliente {
    private final HttpClient http;
    private final URI base;
    private final String servicio;
    private final String secreto;
    private final Gson gson = new Gson();
    private String accessToken;

    public PagoFacilCliente(String baseUrl, String tokenService, String tokenSecret) {
        if (baseUrl == null || tokenService == null || tokenSecret == null ||
                baseUrl.isBlank() || tokenService.isBlank() || tokenSecret.isBlank())
            throw new IllegalArgumentException("Configure la URL y las credenciales de Pago Fácil.");
        this.base = URI.create(baseUrl.replaceAll("/+$", ""));
        this.servicio = tokenService;
        this.secreto = tokenSecret;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public synchronized JsonObject autenticar() throws IOException, InterruptedException {
        JsonObject r = post("/login", null, false, "tcTokenService", servicio, "tcTokenSecret", secreto);
        accessToken = valor(r, "accessToken");
        return r;
    }

    public JsonObject metodosHabilitados() throws IOException, InterruptedException { return protegido("/list-enabled-services", null); }

    public JsonObject generarQr(JsonObject datos) throws IOException, InterruptedException { return protegido("/generate-qr", datos); }

    public JsonObject consultarTransaccion(JsonObject datos) throws IOException, InterruptedException { return protegido("/query-transaction", datos); }

    private JsonObject protegido(String ruta, JsonObject cuerpo) throws IOException, InterruptedException {
        if (accessToken == null || accessToken.isBlank()) autenticar();
        return post(ruta, cuerpo, true);
    }

    private JsonObject post(String ruta, JsonObject cuerpo, boolean bearer, String... extras) throws IOException, InterruptedException {
        var b = HttpRequest.newBuilder(base.resolve(ruta)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").header("Response-Language", "es");
        if (bearer) b.header("Authorization", "Bearer " + accessToken);
        for (int i = 0; i + 1 < extras.length; i += 2) b.header(extras[i], extras[i + 1]);
        if (cuerpo == null) b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(""));
        else b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(gson.toJson(cuerpo)));
        var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() < 200 || r.statusCode() >= 300) throw new IOException("Pago Fácil respondió HTTP " + r.statusCode());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private static String valor(JsonObject respuesta, String campo) throws IOException {
        if (!respuesta.has("values") || !respuesta.get("values").isJsonObject() || !respuesta.getAsJsonObject("values").has(campo))
            throw new IOException("Respuesta de Pago Fácil sin " + campo + ".");
        return respuesta.getAsJsonObject("values").get(campo).getAsString();
    }
}
