package vn.edu.hcmuaf.fit.cuahangdienthoai.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Database;

@Service
public class DatabaseService {
  private final ObjectMapper mapper;
  private final Path dbFile = Path.of("data", "db.json");

  public DatabaseService(ObjectMapper mapper) {
    this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
  }

  public synchronized Database read() {
    ensure();
    try {
      return mapper.readValue(dbFile.toFile(), Database.class);
    } catch (IOException ex) {
      throw new IllegalStateException("Không đọc được database.", ex);
    }
  }

  public synchronized void write(Database db) {
    try {
      Files.createDirectories(dbFile.getParent());
      mapper.writeValue(dbFile.toFile(), db);
    } catch (IOException ex) {
      throw new IllegalStateException("Không ghi được database.", ex);
    }
  }

  public synchronized Database update(DatabaseUpdater updater) {
    Database db = read();
    updater.update(db);
    write(db);
    return db;
  }

  private void ensure() {
    try {
      Files.createDirectories(dbFile.getParent());
      if (!Files.exists(dbFile)) {
        write(new Database());
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Không tạo được database.", ex);
    }
  }

  @FunctionalInterface
  public interface DatabaseUpdater {
    void update(Database db);
  }
}
