package br.com.calendar.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findAllByUser_IdOrderByCreatedAtDesc(String userId);

    boolean existsByIdAndUser_Id(String notificationId, String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true "
            + "WHERE n.id = :notificationId "
            + "AND n.user.id = :userId "
            + "AND n.read = false")
    int markUnreadAsRead(@Param("notificationId") String notificationId, @Param("userId") String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true "
            + "WHERE n.user.id = :userId "
            + "AND n.read = false")
    int markAllUnreadAsRead(@Param("userId") String userId);
}
