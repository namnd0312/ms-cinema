package com.namnd.cinema.repository;

import com.namnd.cinema.model.PasswordHistory;
import com.namnd.cinema.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findTop3ByUserOrderByCreatedAtDesc(User user);

    @Modifying
    @Query("DELETE FROM PasswordHistory ph WHERE ph.user = :user AND ph.id NOT IN " +
           "(SELECT ph2.id FROM PasswordHistory ph2 WHERE ph2.user = :user ORDER BY ph2.createdAt DESC LIMIT 3)")
    void deleteOldEntriesByUser(User user);
}
