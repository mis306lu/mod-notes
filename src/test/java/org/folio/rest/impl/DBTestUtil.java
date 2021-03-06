package org.folio.rest.impl;

import static org.folio.test.util.TestUtil.STUB_TENANT;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Vertx;

import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.persist.PostgresClient;

public class DBTestUtil {

  private static final String JSONB_COLUMN = "jsonb";
  private static final String NOTE_TYPE_TABLE = "note_type";
  private static final String NOTE_TABLE = "note_data";

  private DBTestUtil() {
  }

  public static void insertNoteType(Vertx vertx, String stubId, String tenantId, String json) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertx).execute(
      "INSERT INTO " + getNoteTypesTableName(tenantId) + "(" + " id, " + JSONB_COLUMN + ") VALUES ('" + stubId + "' , '" + json + "');" ,
      event -> future.complete(null));
    future.join();
  }

  public static void insertNote(Vertx vertx, String stubId, String tenantId, String json) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertx).execute(
      "INSERT INTO " + getNoteTableName(tenantId) + "(" + " id, " + JSONB_COLUMN + ") VALUES ('" + stubId + "' , '" + json + "');" ,
      event -> future.complete(null));
    future.join();
  }

  private static String getNoteTypesTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + NOTE_TYPE_TABLE;
  }

  private static String getNoteTableName(String tenantId) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + NOTE_TABLE;
  }


  public static List<NoteType> getAllNoteTypes(Vertx vertx) {
    ObjectMapper mapper = new ObjectMapper();
    CompletableFuture<List<NoteType>> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertx).select(
      "SELECT * FROM " + getNoteTypesTableName(STUB_TENANT),
      event -> future.complete(event.result().getRows().stream()
        .map(row -> row.getString(JSONB_COLUMN))
        .map(json -> parseNoteType(mapper, json))
        .collect(Collectors.toList())));
    return future.join();
  }

  private static void deleteFromTable(Vertx vertx, String tableName) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    PostgresClient.getInstance(vertx).execute(
      "DELETE FROM " + tableName,
      event -> future.complete(null));
    future.join();
  }

  private static NoteType parseNoteType(ObjectMapper mapper, String json) {
    try {
      return mapper.readValue(json, NoteType.class);
    } catch (IOException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Can't parse note type", e);
    }
  }

  public static void deleteAllNoteTypes(Vertx vertx) {
    deleteFromTable(vertx, getNoteTypesTableName(STUB_TENANT));
  }

  public static void deleteAllNotes(Vertx vertx) {
    deleteFromTable(vertx, getNoteTableName(STUB_TENANT));
  }
}
