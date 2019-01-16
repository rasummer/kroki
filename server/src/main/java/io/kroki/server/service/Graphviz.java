package io.kroki.server.service;

import io.kroki.server.action.Commander;
import io.kroki.server.action.Response;
import io.kroki.server.decode.DecodeException;
import io.kroki.server.decode.DiagramSource;
import io.kroki.server.format.ContentType;
import io.kroki.server.format.FileFormat;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Graphviz {

  private static final List<FileFormat> SUPPORTED_FORMATS = Arrays.asList(FileFormat.PNG, FileFormat.SVG, FileFormat.JPEG);
  private static final String supportedFormatList = FileFormat.stringify(SUPPORTED_FORMATS);

  private final Vertx vertx;
  private final String binPath;

  public Graphviz(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.binPath = config.getString("KROKI_DOT_BIN_PATH", "dot");
  }

  public Handler<RoutingContext> convertRoute() {
    return routingContext -> {
      HttpServerResponse response = routingContext.response();
      String outputFormat = routingContext.request().getParam("output_format");
      FileFormat fileFormat = FileFormat.get(outputFormat);
      if (fileFormat == null || !SUPPORTED_FORMATS.contains(fileFormat)) {
        Response.handleUnsupportedFormat(response, outputFormat, supportedFormatList);
        return;
      }
      vertx.executeBlocking(future -> {
        try {
          String sourceEncoded = routingContext.request().getParam("source_encoded");
          byte[] sourceDecoded;
          try {
            sourceDecoded = DiagramSource.decode(sourceEncoded).getBytes();
            byte[] result = dot(sourceDecoded, fileFormat.getName());
            future.complete(result);
          } catch (DecodeException e) {
            future.fail(e);
          }
        } catch (IOException | InterruptedException | IllegalStateException e) {
          future.fail(e);
        }
      }, res -> {
        if (res.failed()) {
          response
            .setStatusCode(400)
            .end(res.cause().getMessage());
          return;
        }
        byte[] result = (byte[]) res.result();
        response
          .putHeader("Content-Type", ContentType.get(fileFormat))
          .end(Buffer.buffer(result));
      });
    };
  }

  private byte[] dot(byte[] source, String format) throws IOException, InterruptedException, IllegalStateException {
    // Supported format:
    // canon cmap cmapx cmapx_np dot dot_json eps fig gd gd2 gif gv imap imap_np ismap
    // jpe jpeg jpg json json0 mp pdf pic plain plain-ext
    // png pov ps ps2
    // svg svgz tk vml vmlz vrml wbmp x11 xdot xdot1.2 xdot1.4 xdot_json xlib
    return Commander.execute(source, binPath, "-T" + format);
  }
}
