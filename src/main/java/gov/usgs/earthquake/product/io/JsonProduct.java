package gov.usgs.earthquake.product.io;

import gov.usgs.earthquake.product.AbstractContent;
import gov.usgs.earthquake.product.Content;
import gov.usgs.earthquake.product.Product;
import gov.usgs.earthquake.product.ProductId;
import gov.usgs.earthquake.product.URLContent;
import gov.usgs.util.CryptoUtils.Version;
import gov.usgs.util.protocolhandlers.data.Handler;
import gov.usgs.util.StreamUtils;
import gov.usgs.util.XmlUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

public class JsonProduct {

  static {
    // make sure data protocol handler registered
    Handler.register();
  }

  /**
   * Convert product object to json.
   *
   * @param product
   * @return
   * @throws Exception
   */
  public JsonObject getJsonObject(final Product product) throws Exception {
    JsonObjectBuilder json = Json.createObjectBuilder();

    json.add("contents", getContentsJson(product.getContents()));
    JsonObjectBuilder geometry = getGeometryJson(product);
    if (geometry == null) {
      json.addNull("geometry");
    } else {
      json.add("geometry", geometry);
    }
    final ProductId id = product.getId();
    json.add("id", getIdJson(id));
    json.add("links", getLinksJson(product.getLinks()));
    json.add("properties", getPropertiesJson(product.getProperties()));
    json.add("signature", product.getSignature());
    json.add("signatureVersion", product.getSignatureVersion().toString());
    json.add("status", product.getStatus());
    json.add("type", "Feature");
    return json.build();
  }

  /**
   * Convert json object to product.
   */
  public Product getProduct(final JsonObject json) throws Exception {
    Product product = new Product(getId(json.getJsonObject("id")));
    product.setContents(getContents(json.getJsonArray("contents")));
    product.setLinks(getLinks(json.getJsonArray("links")));
    product.setProperties(getProperties(json.getJsonObject("properties")));
    product.setStatus(json.getString("status"));
    product.setSignature(json.getString("signature"));
    product.setSignatureVersion(Version.fromString(json.getString("signatureVersion")));
    product.setTrackerURL(new URL("data:,"));
    return product;
  }

  /**
   * Convert contents map to json.
   *
   * @param contents
   * @return
   * @throws Exception
   */
  public JsonArrayBuilder getContentsJson(final Map<String, Content> contents) throws Exception {
    final JsonArrayBuilder builder = Json.createArrayBuilder();
    for (final String path : contents.keySet()) {
      final Content content = contents.get(path);
      final JsonObjectBuilder jsonContent =
          Json.createObjectBuilder()
              .add("length", content.getLength())
              .add("modified", XmlUtils.formatDate(content.getLastModified()))
              .add("path", path)
              .add("sha256", content.getSha256())
              .add("type", content.getContentType());
      if (content instanceof URLContent) {
        jsonContent.add("url", ((URLContent) content).getURL().toString());
      } else if ("".equals(path)) {
        jsonContent.add(
            "url",
            "data:"
                + content.getContentType()
                + ";base64,"
                + Base64.getEncoder()
                    .encodeToString(StreamUtils.readStream(content.getInputStream())));
      } else {
        // no url, will throw parse error
        // this is used to get upload urls, and returned object includes urls...
        jsonContent.addNull("url");
      }
      builder.add(jsonContent);
    }
    return builder;
  }

  /**
   * Convert contents json to map.
   *
   * @param json
   * @return
   * @throws Exception
   */
  public Map<String, Content> getContents(final JsonArray json) throws Exception {
    Map<String, Content> contents = new HashMap<String, Content>();
    for (JsonValue value : json) {
      JsonObject object = value.asJsonObject();
      Long length = object.getJsonNumber("length").longValue();
      Date modified = XmlUtils.getDate(object.getString("modified"));
      String path = object.getString("path");
      String sha256 = object.getString("sha256");
      String type = object.getString("type");
      String url = object.getString("url");

      AbstractContent content = new URLContent(new URL(url));
      content.setContentType(type);
      content.setLastModified(modified);
      content.setLength(length);
      content.setSha256(sha256);
      contents.put(path, content);
    }
    return contents;
  }

  /**
   * Create json geometry from product properties.
   *
   * @param product
   * @return
   * @throws Exception
   */
  public JsonObjectBuilder getGeometryJson(final Product product) throws Exception {
    final BigDecimal latitude = product.getLatitude();
    final BigDecimal longitude = product.getLongitude();
    final BigDecimal depth = product.getDepth();
    if (latitude != null || longitude != null) {
      return Json.createObjectBuilder()
          .add("type", "Point")
          .add("coordinates", Json.createArrayBuilder().add(longitude).add(latitude).add(depth));
    }
    return null;
  }

  /**
   * Convert json id to ProductId object.
   *
   * @param json
   * @return
   * @throws Exception
   */
  public ProductId getId(final JsonObject json) throws Exception {
    final String code = json.getString("code");
    final String source = json.getString("source");
    final String type = json.getString("type");
    final Date updateTime = XmlUtils.getDate(json.getString("updateTime"));
    return new ProductId(source, type, code, updateTime);
  }

  /**
   * Convert ProductId to json object.
   * @param id
   * @return
   * @throws Exception
   */
  public JsonObjectBuilder getIdJson(final ProductId id) throws Exception {
    return Json.createObjectBuilder()
        .add("code", id.getCode())
        .add("source", id.getSource())
        .add("type", id.getType())
        .add("updateTime", XmlUtils.formatDate(id.getUpdateTime()));
  }

  /**
   * Convert json links to map.
   * @param json
   * @return
   * @throws Exception
   */
  public Map<String, List<URI>> getLinks(final JsonArray json) throws Exception {
    final Map<String, List<URI>> links = new HashMap<String, List<URI>>();
    for (final JsonValue value : json) {
      final JsonObject link = value.asJsonObject();
      final String relation = link.getString("relation");
      final URI uri = new URI(link.getString("uri"));
      List<URI> relationLinks = links.get(relation);
      if (relationLinks == null) {
        relationLinks = new ArrayList<URI>();
        links.put(relation, relationLinks);
      }
      relationLinks.add(uri);
    }
    return links;
  }

  /**
   * Convert links map to json.
   *
   * @param links
   * @return
   * @throws Exception
   */
  public JsonArrayBuilder getLinksJson(final Map<String, List<URI>> links) throws Exception {
    final JsonArrayBuilder builder = Json.createArrayBuilder();
    for (final String relation : links.keySet()) {
      final List<URI> relationLinks = links.get(relation);
      for (final URI uri : relationLinks) {
        builder.add(
            Json.createObjectBuilder().add("relation", relation).add("uri", uri.toString()));
      }
    }
    return builder;
  }

  /**
   * Convert properties json to map.
   * @param json
   * @return
   * @throws Exception
   */
  public Map<String, String> getProperties(final JsonObject json) throws Exception {
    final Map<String, String> properties = new HashMap<String, String>();
    for (final String name : json.keySet()) {
      properties.put(name, json.getString(name));
    }
    return properties;
  }

  /**
   * Convert properties map to json.
   *
   * @param properties
   * @return
   * @throws Exception
   */
  public JsonObjectBuilder getPropertiesJson(final Map<String, String> properties)
      throws Exception {
    final JsonObjectBuilder builder = Json.createObjectBuilder();
    for (final String name : properties.keySet()) {
      builder.add(name, properties.get(name));
    }
    return builder;
  }
}
