package Capstone.CSmart.global.service.student;

import Capstone.CSmart.global.domain.entity.Student;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 학생 정보 업데이트 공통 서비스
 * Gemini API 추출 정보 및 프론트엔드 수동 입력 정보를 Student 엔티티에 정교하게 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentInfoUpdateService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 전화번호 정규식 패턴
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^(010|011|016|017|018|019)-?\\d{3,4}-?\\d{4}$"
    );
    
    // 학년 패턴 (1학년, 2학년, 3학년, 4학년, 1, 2, 3, 4 등)
    private static final Pattern GRADE_PATTERN = Pattern.compile(
        "^(1|2|3|4)(학년)?$"
    );

    /**
     * 추출된 정보로 Student 엔티티 업데이트
     * 모든 필드를 정교하게 검증하고 정규화하여 저장
     * 
     * @param student 업데이트할 Student 엔티티
     * @param extractedInfo Gemini API 또는 프론트엔드에서 받은 학생 정보 Map
     * @return 업데이트된 Student 엔티티
     */
    @Transactional
    public Student updateStudentInfo(Student student, Map<String, Object> extractedInfo) {
        if (extractedInfo == null || extractedInfo.isEmpty()) {
            log.warn("추출된 정보가 비어있습니다: studentId={}", student.getStudentId());
            return student;
        }

        boolean updated = false;

        // 1. 이름 업데이트 (필수 필드, 항상 업데이트 가능)
        if (extractedInfo.containsKey("name")) {
            String name = normalizeString(extractedInfo.get("name"));
            if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("null")) {
                // 이름 길이 제한 (100자)
                if (name.length() <= 100) {
                    if (!name.equals(student.getName())) {
                        student.setName(name);
                        updated = true;
                        log.info("학생 이름 업데이트: studentId={}, name={}", 
                                student.getStudentId(), name);
                    }
                } else {
                    log.warn("이름이 너무 깁니다 (100자 초과): studentId={}, name length={}", 
                            student.getStudentId(), name.length());
                }
            }
        }

        // 2. 나이 업데이트 (유효성 검증 포함)
        if (extractedInfo.containsKey("age")) {
            Integer age = parseAge(extractedInfo.get("age"));
            if (age != null && age > 0 && age < 150) {
                if (!age.equals(student.getAge())) {
                    student.setAge(age);
                    updated = true;
                    log.info("학생 나이 업데이트: studentId={}, age={}", 
                            student.getStudentId(), age);
                }
            } else if (age != null) {
                log.warn("유효하지 않은 나이: studentId={}, age={}", 
                        student.getStudentId(), age);
            }
        }

        // 3. 전적 대학 업데이트
        if (extractedInfo.containsKey("previousSchool")) {
            String previousSchool = normalizeString(extractedInfo.get("previousSchool"));
            if (previousSchool != null && !previousSchool.isEmpty() && 
                !previousSchool.equalsIgnoreCase("null")) {
                // 길이 제한 (255자)
                if (previousSchool.length() <= 255) {
                    if (!previousSchool.equals(student.getPreviousSchool())) {
                        student.setPreviousSchool(previousSchool);
                        updated = true;
                        log.info("전적 대학 업데이트: studentId={}, previousSchool={}", 
                                student.getStudentId(), previousSchool);
                    }
                } else {
                    log.warn("전적 대학명이 너무 깁니다 (255자 초과): studentId={}", 
                            student.getStudentId());
                }
            }
        }

        // 4. 목표 대학 업데이트
        if (extractedInfo.containsKey("targetUniversity")) {
            String targetUniversity = normalizeString(extractedInfo.get("targetUniversity"));
            if (targetUniversity != null && !targetUniversity.isEmpty() && 
                !targetUniversity.equalsIgnoreCase("null")) {
                // 길이 제한 (255자)
                if (targetUniversity.length() <= 255) {
                    if (!targetUniversity.equals(student.getTargetUniversity())) {
                        student.setTargetUniversity(targetUniversity);
                        updated = true;
                        log.info("목표 대학 업데이트: studentId={}, targetUniversity={}", 
                                student.getStudentId(), targetUniversity);
                    }
                } else {
                    log.warn("목표 대학명이 너무 깁니다 (255자 초과): studentId={}", 
                            student.getStudentId());
                }
            }
        }

        // 5. 전화번호 업데이트 (정규식 검증)
        if (extractedInfo.containsKey("phoneNumber")) {
            String phoneNumber = normalizePhoneNumber(extractedInfo.get("phoneNumber"));
            if (phoneNumber != null && !phoneNumber.isEmpty() && 
                !phoneNumber.equalsIgnoreCase("null")) {
                // 길이 제한 (30자)
                if (phoneNumber.length() <= 30) {
                    if (!phoneNumber.equals(student.getPhoneNumber())) {
                        student.setPhoneNumber(phoneNumber);
                        updated = true;
                        log.info("전화번호 업데이트: studentId={}, phoneNumber={}", 
                                student.getStudentId(), phoneNumber);
                    }
                } else {
                    log.warn("전화번호가 너무 깁니다 (30자 초과): studentId={}", 
                            student.getStudentId());
                }
            }
        }

        // 6. 희망 전공 업데이트
        if (extractedInfo.containsKey("desiredMajor")) {
            String desiredMajor = normalizeString(extractedInfo.get("desiredMajor"));
            if (desiredMajor != null && !desiredMajor.isEmpty() && 
                !desiredMajor.equalsIgnoreCase("null")) {
                // 길이 제한 (100자)
                if (desiredMajor.length() <= 100) {
                    if (!desiredMajor.equals(student.getDesiredMajor())) {
                        student.setDesiredMajor(desiredMajor);
                        updated = true;
                        log.info("희망 전공 업데이트: studentId={}, desiredMajor={}", 
                                student.getStudentId(), desiredMajor);
                    }
                } else {
                    log.warn("희망 전공명이 너무 깁니다 (100자 초과): studentId={}", 
                            student.getStudentId());
                }
            }
        }

        // 7. 현재 학년 업데이트 (정규식 검증)
        if (extractedInfo.containsKey("currentGrade")) {
            String currentGrade = normalizeGrade(extractedInfo.get("currentGrade"));
            if (currentGrade != null && !currentGrade.isEmpty() && 
                !currentGrade.equalsIgnoreCase("null")) {
                // 길이 제한 (50자)
                if (currentGrade.length() <= 50) {
                    if (!currentGrade.equals(student.getCurrentGrade())) {
                        student.setCurrentGrade(currentGrade);
                        updated = true;
                        log.info("현재 학년 업데이트: studentId={}, currentGrade={}", 
                                student.getStudentId(), currentGrade);
                    }
                } else {
                    log.warn("현재 학년이 너무 깁니다 (50자 초과): studentId={}", 
                            student.getStudentId());
                }
            }
        }

        // 8. 희망 입학 학기 업데이트
        if (extractedInfo.containsKey("desiredSemester")) {
            String desiredSemester = normalizeString(extractedInfo.get("desiredSemester"));
            if (desiredSemester != null && !desiredSemester.isEmpty() && 
                !desiredSemester.equalsIgnoreCase("null")) {
                // 길이 제한 (50자)
                if (desiredSemester.length() <= 50) {
                    if (!desiredSemester.equals(student.getDesiredSemester())) {
                        student.setDesiredSemester(desiredSemester);
                        updated = true;
                        log.info("희망 입학 학기 업데이트: studentId={}, desiredSemester={}", 
                                student.getStudentId(), desiredSemester);
                    }
                } else {
                    log.warn("희망 입학 학기가 너무 깁니다 (50자 초과): studentId={}", 
                            student.getStudentId());
                }
            }
        }

        // 9. type (일반/학사) 업데이트
        if (extractedInfo.containsKey("type")) {
            String type = normalizeString(extractedInfo.get("type"));
            if (type != null && !type.isEmpty() && !type.equalsIgnoreCase("null")) {
                if (type.length() <= 20) {
                    if (!type.equals(student.getType())) {
                        student.setType(type);
                        updated = true;
                        log.info("type 업데이트: studentId={}, type={}", 
                                student.getStudentId(), type);
                    }
                }
            }
        }

        // 10. track (문과/이과/특성화고/예체능/기타) 업데이트
        if (extractedInfo.containsKey("track")) {
            String track = normalizeString(extractedInfo.get("track"));
            if (track != null && !track.isEmpty() && !track.equalsIgnoreCase("null")) {
                if (track.length() <= 50) {
                    if (!track.equals(student.getTrack())) {
                        student.setTrack(track);
                        updated = true;
                        log.info("track 업데이트: studentId={}, track={}", 
                                student.getStudentId(), track);
                    }
                }
            }
        }

        // 11. mathGrade (수학 등급) 업데이트
        if (extractedInfo.containsKey("mathGrade")) {
            String mathGrade = normalizeString(extractedInfo.get("mathGrade"));
            if (mathGrade != null && !mathGrade.isEmpty() && !mathGrade.equalsIgnoreCase("null")) {
                if (mathGrade.length() <= 50) {
                    if (!mathGrade.equals(student.getMathGrade())) {
                        student.setMathGrade(mathGrade);
                        updated = true;
                        log.info("mathGrade 업데이트: studentId={}, mathGrade={}", 
                                student.getStudentId(), mathGrade);
                    }
                }
            }
        }

        // 12. englishGrade (영어 등급) 업데이트
        if (extractedInfo.containsKey("englishGrade")) {
            String englishGrade = normalizeString(extractedInfo.get("englishGrade"));
            if (englishGrade != null && !englishGrade.isEmpty() && !englishGrade.equalsIgnoreCase("null")) {
                if (englishGrade.length() <= 50) {
                    if (!englishGrade.equals(student.getEnglishGrade())) {
                        student.setEnglishGrade(englishGrade);
                        updated = true;
                        log.info("englishGrade 업데이트: studentId={}, englishGrade={}", 
                                student.getStudentId(), englishGrade);
                    }
                }
            }
        }

        // 13. previousCourse (수강했던 편입인강 or 학원과 진도) 업데이트
        if (extractedInfo.containsKey("previousCourse")) {
            String previousCourse = normalizeString(extractedInfo.get("previousCourse"));
            if (previousCourse != null && !previousCourse.isEmpty() && !previousCourse.equalsIgnoreCase("null")) {
                if (previousCourse.length() <= 255) {
                    if (!previousCourse.equals(student.getPreviousCourse())) {
                        student.setPreviousCourse(previousCourse);
                        updated = true;
                        log.info("previousCourse 업데이트: studentId={}, previousCourse={}", 
                                student.getStudentId(), previousCourse);
                    }
                }
            }
        }

        // 14. isRetaking (편입재수 여부) 업데이트
        if (extractedInfo.containsKey("isRetaking")) {
            Boolean isRetaking = parseBoolean(extractedInfo.get("isRetaking"));
            if (isRetaking != null && !isRetaking.equals(student.getIsRetaking())) {
                student.setIsRetaking(isRetaking);
                updated = true;
                log.info("isRetaking 업데이트: studentId={}, isRetaking={}", 
                        student.getStudentId(), isRetaking);
            }
        }

        // 15. isSunungRetaking (수능재수 여부) 업데이트
        if (extractedInfo.containsKey("isSunungRetaking")) {
            Boolean isSunungRetaking = parseBoolean(extractedInfo.get("isSunungRetaking"));
            if (isSunungRetaking != null && !isSunungRetaking.equals(student.getIsSunungRetaking())) {
                student.setIsSunungRetaking(isSunungRetaking);
                updated = true;
                log.info("isSunungRetaking 업데이트: studentId={}, isSunungRetaking={}", 
                        student.getStudentId(), isSunungRetaking);
            }
        }

        // 16. hasToeic (토익 취득 여부) 업데이트
        if (extractedInfo.containsKey("hasToeic")) {
            Boolean hasToeic = parseBoolean(extractedInfo.get("hasToeic"));
            if (hasToeic != null && !hasToeic.equals(student.getHasToeic())) {
                student.setHasToeic(hasToeic);
                updated = true;
                log.info("hasToeic 업데이트: studentId={}, hasToeic={}", 
                        student.getStudentId(), hasToeic);
            }
        }

        // 17. hasPartTimeJob (알바 유무) 업데이트
        if (extractedInfo.containsKey("hasPartTimeJob")) {
            Boolean hasPartTimeJob = parseBoolean(extractedInfo.get("hasPartTimeJob"));
            if (hasPartTimeJob != null && !hasPartTimeJob.equals(student.getHasPartTimeJob())) {
                student.setHasPartTimeJob(hasPartTimeJob);
                updated = true;
                log.info("hasPartTimeJob 업데이트: studentId={}, hasPartTimeJob={}", 
                        student.getStudentId(), hasPartTimeJob);
            }
        }

        // 18. availableCallTime (통화 가능 시간) 업데이트
        if (extractedInfo.containsKey("availableCallTime")) {
            String availableCallTime = normalizeString(extractedInfo.get("availableCallTime"));
            if (availableCallTime != null && !availableCallTime.isEmpty() && !availableCallTime.equalsIgnoreCase("null")) {
                if (availableCallTime.length() <= 100) {
                    if (!availableCallTime.equals(student.getAvailableCallTime())) {
                        student.setAvailableCallTime(availableCallTime);
                        updated = true;
                        log.info("availableCallTime 업데이트: studentId={}, availableCallTime={}", 
                                student.getStudentId(), availableCallTime);
                    }
                }
            }
        }

        // 19. message (꼭 하고 싶은 말) 업데이트
        if (extractedInfo.containsKey("message")) {
            String message = normalizeText(extractedInfo.get("message"));
            if (message != null && !message.isEmpty() && !message.equalsIgnoreCase("null")) {
                if (!message.equals(student.getMessage())) {
                    student.setMessage(message);
                    updated = true;
                    log.info("message 업데이트: studentId={}, message length={}", 
                            student.getStudentId(), message.length());
                }
            }
        }

        // 20. source (유입경로) 업데이트
        if (extractedInfo.containsKey("source")) {
            String source = normalizeString(extractedInfo.get("source"));
            if (source != null && !source.isEmpty() && !source.equalsIgnoreCase("null")) {
                if (source.length() <= 100) {
                    if (!source.equals(student.getSource())) {
                        student.setSource(source);
                        updated = true;
                        log.info("source 업데이트: studentId={}, source={}", 
                                student.getStudentId(), source);
                    }
                }
            }
        }

        // 21. 상담 데이터 JSON으로 저장 (전체 extractedInfo 저장)
        try {
            String jsonData = objectMapper.writeValueAsString(extractedInfo);
            if (jsonData != null && !jsonData.isEmpty()) {
                student.setConsultationFormDataJson(jsonData);
                updated = true;
                log.debug("상담 데이터 JSON 저장: studentId={}, json length={}", 
                        student.getStudentId(), jsonData.length());
            }
        } catch (Exception e) {
            log.warn("상담 데이터 JSON 변환 실패: studentId={}", 
                    student.getStudentId(), e);
        }

        if (updated) {
            log.info("학생 정보 업데이트 완료: studentId={}, 업데이트된 필드 수={}", 
                    student.getStudentId(), countUpdatedFields(extractedInfo));
        } else {
            log.debug("학생 정보 업데이트 없음: studentId={}", student.getStudentId());
        }

        return student;
    }

    /**
     * 문자열 정규화 (null 체크, trim, 빈 문자열 처리, 맞춤법/띄어쓰기 정규화)
     */
    private String normalizeString(Object value) {
        if (value == null) {
            return null;
        }
        
        String str = value.toString().trim();
        if (str.isEmpty() || str.equalsIgnoreCase("null") || str.equalsIgnoreCase("none")) {
            return null;
        }
        
        // 번호 제거 (예: "1. 이성재" -> "이성재")
        str = str.replaceFirst("^\\d+\\.\\s*", "");
        
        // 맞춤법/띄어쓰기 정규화
        str = normalizeSpacing(str);
        
        return str;
    }

    /**
     * 텍스트 정규화 (맞춤법/띄어쓰기 강화 버전)
     */
    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        
        String str = value.toString().trim();
        if (str.isEmpty() || str.equalsIgnoreCase("null") || str.equalsIgnoreCase("none")) {
            return null;
        }
        
        // 번호 제거
        str = str.replaceFirst("^\\d+\\.\\s*", "");
        
        // 맞춤법/띄어쓰기 정규화 (더 강화)
        str = normalizeSpacing(str);
        
        // 흔한 맞춤법 오류 수정
        str = str.replace("학 습", "학습")
                 .replace("채찍질해주시기", "채찍질해 주시기")
                 .replace("채찍질해주시", "채찍질해 주시")
                 .replace("  ", " ") // 연속 공백 제거
                 .trim();
        
        return str;
    }

    /**
     * 띄어쓰기 정규화 (한글 띄어쓰기 규칙 적용)
     */
    private String normalizeSpacing(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 연속 공백 제거
        text = text.replaceAll("\\s+", " ");
        
        // 조사 앞 공백 제거 (예: "학 습" -> "학습")
        text = text.replaceAll("([가-힣])\\s+([을를이가은는도만])\\s", "$1$2 ");
        
        // 동사/형용사와 어미 사이 공백 제거
        text = text.replaceAll("([가-힣])\\s+([해하할할까요겠습니다])\\s", "$1$2 ");
        
        return text.trim();
    }

    /**
     * 불리언 값 파싱 ("예", "있음", "있습니다" -> true, "아니오", "없음", "없습니다" -> false)
     */
    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        String str = value.toString().trim().toLowerCase();
        
        if (str.isEmpty() || str.equalsIgnoreCase("null")) {
            return null;
        }
        
        // true로 변환되는 경우
        if (str.equals("true") || str.equals("예") || str.equals("있음") || 
            str.equals("있습니다") || str.equals("yes") || str.equals("y") ||
            str.equals("1") || str.equals("있어요") || str.equals("있어")) {
            return true;
        }
        
        // false로 변환되는 경우
        if (str.equals("false") || str.equals("아니오") || str.equals("없음") || 
            str.equals("없습니다") || str.equals("no") || str.equals("n") ||
            str.equals("0") || str.equals("없어요") || str.equals("없어")) {
            return false;
        }
        
        log.warn("불리언 파싱 실패 (기본값 null): value={}", value);
        return null;
    }

    /**
     * 나이 파싱 및 검증
     */
    private Integer parseAge(Object value) {
        if (value == null) {
            return null;
        }
        
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                String str = ((String) value).trim();
                if (str.isEmpty() || str.equalsIgnoreCase("null")) {
                    return null;
                }
                return Integer.parseInt(str);
            }
        } catch (NumberFormatException e) {
            log.warn("나이 파싱 실패: value={}", value);
        }
        
        return null;
    }

    /**
     * 전화번호 정규화 및 검증
     */
    private String normalizePhoneNumber(Object value) {
        String phoneNumber = normalizeString(value);
        if (phoneNumber == null) {
            return null;
        }
        
        // 하이픈 제거 후 다시 추가 (010-1234-5678 형식)
        String normalized = phoneNumber.replaceAll("[^0-9]", "");
        
        if (normalized.length() == 11) {
            // 01012345678 -> 010-1234-5678
            normalized = normalized.substring(0, 3) + "-" + 
                        normalized.substring(3, 7) + "-" + 
                        normalized.substring(7);
        } else if (normalized.length() == 10) {
            // 0101234567 -> 010-1234-567
            normalized = normalized.substring(0, 3) + "-" + 
                        normalized.substring(3, 6) + "-" + 
                        normalized.substring(6);
        }
        
        // 정규식 검증
        if (PHONE_PATTERN.matcher(normalized).matches()) {
            return normalized;
        } else {
            log.warn("유효하지 않은 전화번호 형식: {}", phoneNumber);
            // 검증 실패해도 원본 반환 (DB 제약에서 걸러짐)
            return phoneNumber.length() <= 30 ? phoneNumber : phoneNumber.substring(0, 30);
        }
    }

    /**
     * 학년 정규화 (1학년, 2학년, 3학년, 4학년 형식으로 통일)
     */
    private String normalizeGrade(Object value) {
        String grade = normalizeString(value);
        if (grade == null) {
            return null;
        }
        
        // 숫자만 추출
        String digits = grade.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            int gradeNum = Integer.parseInt(digits);
            if (gradeNum >= 1 && gradeNum <= 4) {
                return gradeNum + "학년";
            }
        }
        
        // 정규식 검증
        if (GRADE_PATTERN.matcher(grade).matches()) {
            String num = grade.replaceAll("[^0-9]", "");
            if (!num.isEmpty()) {
                return num + "학년";
            }
        }
        
        // 검증 실패해도 원본 반환 (길이 제한만 체크)
        return grade.length() <= 50 ? grade : grade.substring(0, 50);
    }

    /**
     * 업데이트된 필드 수 카운트
     */
    private int countUpdatedFields(Map<String, Object> extractedInfo) {
        int count = 0;
        String[] fields = {
            "name", "age", "type", "previousSchool", "targetUniversity", 
            "phoneNumber", "desiredMajor", "currentGrade", "desiredSemester",
            "track", "mathGrade", "englishGrade", "previousCourse",
            "isRetaking", "isSunungRetaking", "hasToeic", "hasPartTimeJob",
            "availableCallTime", "message", "source"
        };
        
        for (String field : fields) {
            if (extractedInfo.containsKey(field) && extractedInfo.get(field) != null) {
                count++;
            }
        }
        
        return count;
    }
}

