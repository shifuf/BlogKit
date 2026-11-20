package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.repository.SiteSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

/** Music credentials are only decrypted inside the backend. */
@Service
public class MusicSettingsService {
    private static final List<String> KEYS = List.of("cyQqKey", "cyNeteaseKey", "chkszKey");
    private final SiteSettingRepository repository;
    private final DownloadCipher cipher;
    private final Map<String, String> initial;

    public MusicSettingsService(SiteSettingRepository repository, DownloadCipher cipher,
            @Value("${blog.music.api-key:}") String qq,
            @Value("${blog.music.netease-api-key:}") String netease,
            @Value("${blog.music.chksz-api-key:}") String chksz) {
        this.repository = repository;
        this.cipher = cipher;
        this.initial = Map.of("cyQqKey", qq, "cyNeteaseKey", netease, "chkszKey", chksz);
    }

    public String key(String name) {
        if (!KEYS.contains(name)) throw new BusinessException("未知音乐配置");
        return repository.findBySettingGroupAndSettingKey("music", name).map(s -> {
            if (s.getSettingValue() == null || s.getSettingValue().isBlank()) return "";
            String plain = cipher.open(s.getSettingValue());
            if (!plain.startsWith("music-key\t")) throw new BusinessException("音乐密钥配置无效，请重新保存");
            return plain.substring(10);
        }).orElse(initial.get(name)).trim();
    }

    public List<SiteSetting> list() {
        return KEYS.stream().map(name -> {
            SiteSetting item = new SiteSetting();
            item.setSettingGroup("music"); item.setSettingKey(name);
            item.setSettingValue(key(name).isBlank() ? "" : "********");
            return item;
        }).toList();
    }

    @Transactional
    public List<SiteSetting> save(Map<String, String> values) {
        for (String name : KEYS) {
            if (!values.containsKey(name)) continue;
            String value = values.get(name) == null ? "" : values.get(name).trim();
            if ("********".equals(value)) continue;
            if (value.length() > 512) throw new BusinessException("音乐密钥过长");
            SiteSetting item = repository.findBySettingGroupAndSettingKey("music", name).orElseGet(SiteSetting::new);
            item.setSettingGroup("music"); item.setSettingKey(name);
            item.setSettingValue(value.isBlank() ? "" : cipher.seal("music-key\t" + value));
            item.setDescription("音乐接口密钥（密封存储）");
            repository.save(item);
        }
        return list();
    }
}
