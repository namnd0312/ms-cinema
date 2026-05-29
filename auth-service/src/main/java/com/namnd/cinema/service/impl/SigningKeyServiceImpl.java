package com.namnd.cinema.service.impl;

import com.namnd.cinema.model.KeyStatus;
import com.namnd.cinema.model.SigningKey;
import com.namnd.cinema.repository.SigningKeyRepository;
import com.namnd.cinema.service.SigningKeyService;
import com.namnd.cinema.util.RsaKeyCryptoUtil;
import com.namnd.kafka.events.audit.Auditable;
import com.namnd.kafka.events.domain.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SigningKeyServiceImpl implements SigningKeyService {

    private final SigningKeyRepository repository;

    @Value("${namnd.app.signingKeyEncryptionPassword}")
    private String encryptionPassword;

    @Override
    public Optional<SigningKey> findActive() {
        return repository.findFirstByStatus(KeyStatus.ACTIVE);
    }

    @Override
    public List<SigningKey> findActiveAndRetired() {
        return repository.findByStatusIn(List.of(KeyStatus.ACTIVE, KeyStatus.RETIRED));
    }

    @Override
    @Transactional
    public SigningKey generateAndPersistActive() {
        KeyPair kp = RsaKeyCryptoUtil.generateRsa2048();
        String kid = generateKid();
        String publicPem = RsaKeyCryptoUtil.toPemPublic(kp.getPublic());
        String privateEncrypted = RsaKeyCryptoUtil.encryptPrivatePem(
                kp.getPrivate(), encryptionPassword.toCharArray());

        SigningKey entity = SigningKey.builder()
                .kid(kid)
                .algorithm("RS256")
                .publicKey(publicPem)
                .privateKeyEncrypted(privateEncrypted)
                .status(KeyStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        SigningKey saved = repository.save(entity);
        log.info("Generated ACTIVE RSA-2048 signing key kid={}", saved.getKid());
        return saved;
    }

    @Override
    public PublicKey loadPublicKey(SigningKey key) {
        return RsaKeyCryptoUtil.fromPemPublic(key.getPublicKey());
    }

    @Override
    public PrivateKey loadPrivateKey(SigningKey key) {
        return RsaKeyCryptoUtil.decryptPrivatePem(
                key.getPrivateKeyEncrypted(), encryptionPassword.toCharArray());
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.UPDATE, entityType = "SigningKey")
    public SigningKey rotate() {
        SigningKey active = repository.findFirstByStatus(KeyStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "No ACTIVE signing key to rotate; bootstrap should have run"));
        active.setStatus(KeyStatus.RETIRED);
        active.setRetiredAt(LocalDateTime.now());
        repository.save(active);
        log.info("Retired signing key kid={}", active.getKid());
        return generateAndPersistActive();
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.DELETE, entityType = "SigningKey")
    public void deleteRetired(String kid) {
        SigningKey k = repository.findByKid(kid)
                .orElseThrow(() -> new java.util.NoSuchElementException("Unknown kid: " + kid));
        if (k.getStatus() != KeyStatus.RETIRED) {
            throw new IllegalStateException("Refusing to delete non-RETIRED key kid=" + kid);
        }
        repository.deleteByKid(kid);
        log.warn("Hard-deleted RETIRED signing key kid={}", kid);
    }

    /** kid format: k-yyyyMMdd-NN where NN counts same-day keys for uniqueness. */
    private String generateKid() {
        String prefix = "k-" + LocalDate.now().toString().replace("-", "");
        long sameDayCount = repository.findAll().stream()
                .filter(k -> k.getKid() != null && k.getKid().startsWith(prefix))
                .count();
        return prefix + "-" + String.format("%02d", sameDayCount + 1);
    }
}
