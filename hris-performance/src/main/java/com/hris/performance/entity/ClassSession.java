package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * 培训班次（域：绩效/培训，分层：实体/DO）。某门课程的一期开课实例（时间/容量/讲师）。
 */
@TableName("prf_class_session")
public class ClassSession extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long courseId;  // 课程ID(逻辑外键 prf_course.id)

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
    private Long instructorId;  // 讲师ID(逻辑外键 prf_instructor.id)

    public Long getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(Long instructorId) {
        this.instructorId = instructorId;
    }
    private LocalDateTime startAt;  // 开课时间(UTC)

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }
    private Integer capacity;  // 容量

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
}
