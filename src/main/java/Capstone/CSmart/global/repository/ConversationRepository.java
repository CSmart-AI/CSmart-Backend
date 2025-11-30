package Capstone.CSmart.global.repository;

import Capstone.CSmart.global.domain.entity.Conversation;
import Capstone.CSmart.global.domain.entity.Student;
import Capstone.CSmart.global.domain.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    /**
     * 특정 학생의 모든 대화 기록 조회 (시간순 정렬)
     */
    List<Conversation> findByStudentOrderByCreatedAtAsc(Student student);
    
    /**
     * 특정 학생의 특정 채널 대화 기록 조회
     */
    List<Conversation> findByStudentAndChannelTypeOrderByCreatedAtAsc(Student student, ChannelType channelType);
    
    /**
     * 특정 학생의 대화 개수 조회
     */
    long countByStudent(Student student);
}

