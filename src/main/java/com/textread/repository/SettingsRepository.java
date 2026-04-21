package com.textread.repository;

import com.textread.model.AppSettings;
import com.textread.model.ReadRegion;
import com.textread.model.ReadingMode;
import com.textread.service.detection.AdFilterMethod;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SettingsRepository {
    private final String jdbcUrl;
    private final String user;
    private final String password;

    public SettingsRepository(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        initTable();
    }

    public AppSettings loadOrDefault() {
        AppSettings defaults = new AppSettings();
        String sql = """
                SELECT TOP 1 region_x, region_y, region_width, region_height,
                             tessdata_path, ocr_language, voice_name, reading_mode, speech_rate, volume, scroll_speed, pitch, muted, ad_filters
                FROM app_settings
                ORDER BY id DESC
                """;
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return defaults;
            }
            AppSettings settings = new AppSettings();
            settings.setReadRegion(new ReadRegion(
                    rs.getInt("region_x"),
                    rs.getInt("region_y"),
                    rs.getInt("region_width"),
                    rs.getInt("region_height")
            ));
            settings.setTessDataPath(rs.getString("tessdata_path"));
            settings.setOcrLanguage(rs.getString("ocr_language"));
            settings.setVoiceName(rs.getString("voice_name"));
            settings.setReadingMode(parseReadingMode(rs.getString("reading_mode")));
            settings.setSpeechRate(rs.getInt("speech_rate"));
            settings.setVolume(rs.getInt("volume"));
            settings.setScrollSpeed(rs.getInt("scroll_speed"));
            settings.setPitch(rs.getInt("pitch"));
            settings.setMuted(rs.getBoolean("muted"));

            settings.getAdFilters().clear();
            settings.getAdFilters().addAll(parseAdFilters(rs.getString("ad_filters")));
            return settings;
        } catch (SQLException ignored) {
            return defaults;
        }
    }

    public void save(AppSettings settings) {
        String sql = """
                INSERT INTO app_settings(region_x, region_y, region_width, region_height, tessdata_path, ocr_language, voice_name,
                                         reading_mode, speech_rate, volume, scroll_speed, pitch, muted, ad_filters)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, settings.getReadRegion().x());
            ps.setInt(2, settings.getReadRegion().y());
            ps.setInt(3, settings.getReadRegion().width());
            ps.setInt(4, settings.getReadRegion().height());
            ps.setString(5, settings.getTessDataPath());
            ps.setString(6, settings.getOcrLanguage());
            ps.setString(7, settings.getVoiceName());
            ps.setString(8, settings.getReadingMode().name());
            ps.setInt(9, settings.getSpeechRate());
            ps.setInt(10, settings.getVolume());
            ps.setInt(11, settings.getScrollSpeed());
            ps.setInt(12, settings.getPitch());
            ps.setBoolean(13, settings.isMuted());
            ps.setString(14, formatAdFilters(settings.getAdFilters()));
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    private void initTable() {
        String ddl = """
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='app_settings' AND xtype='U')
                CREATE TABLE app_settings (
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    region_x INT NOT NULL,
                    region_y INT NOT NULL,
                    region_width INT NOT NULL,
                    region_height INT NOT NULL,
                    tessdata_path NVARCHAR(255) NOT NULL,
                    ocr_language NVARCHAR(32) NOT NULL,
                    voice_name NVARCHAR(64) NOT NULL,
                    reading_mode NVARCHAR(16) NOT NULL DEFAULT 'AUTO',
                    speech_rate INT NOT NULL,
                    volume INT NOT NULL DEFAULT 100,
                    scroll_speed INT NOT NULL,
                    pitch INT NOT NULL,
                    muted BIT NOT NULL,
                    ad_filters NVARCHAR(255) NOT NULL,
                    created_at DATETIME2 DEFAULT SYSUTCDATETIME()
                )
                IF COL_LENGTH('app_settings', 'volume') IS NULL
                    ALTER TABLE app_settings ADD volume INT NOT NULL DEFAULT 100
                IF COL_LENGTH('app_settings', 'reading_mode') IS NULL
                    ALTER TABLE app_settings ADD reading_mode NVARCHAR(16) NOT NULL DEFAULT 'AUTO'
                """;
        try (Connection conn = connect();
             Statement st = conn.createStatement()) {
            st.execute(ddl);
        } catch (SQLException ignored) {
        }
    }

    private String formatAdFilters(Set<AdFilterMethod> filters) {
        return filters.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private Set<AdFilterMethod> parseAdFilters(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.noneOf(AdFilterMethod.class);
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(s -> {
                    try {
                        return AdFilterMethod.valueOf(s);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(v -> v != null)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AdFilterMethod.class)));
    }

    private ReadingMode parseReadingMode(String value) {
        if (value == null || value.isBlank()) {
            return ReadingMode.AUTO;
        }
        try {
            return ReadingMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ReadingMode.AUTO;
        }
    }
}
