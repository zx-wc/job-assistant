package com.nuist.agent.job.position;

import com.nuist.agent.zhangxu.common.ErrorCode;
import com.nuist.agent.entity.JobPosition;
import com.nuist.agent.zhangxu.exception.BusinessException;
import com.nuist.agent.zhangxu.common.PrivacyMasker;
import com.nuist.agent.zhangxu.dto.JobDtos;
import com.nuist.agent.zhangxu.dto.ViewDtos;
import com.nuist.agent.zhangxu.rag.JobVectorService;
import com.nuist.agent.zhangxu.rag.JobVectorServiceImpl;
import com.nuist.agent.mapper.JobPositionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 岗位信息管理实现（成员 B）。
 * 岗位描述与任职要求同样做敏感信息脱敏，保证入库内容合规。
 */
@Service
public class JobPositionServiceImpl implements JobPositionService {

    private static final Logger log = LoggerFactory.getLogger(JobPositionServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final JobPositionMapper positionMapper;
    private final PrivacyMasker privacyMasker;
    private final JobVectorService vectorService;

    public JobPositionServiceImpl(JobPositionMapper positionMapper, PrivacyMasker privacyMasker,
                                  JobVectorService vectorService) {
        this.positionMapper = positionMapper;
        this.privacyMasker = privacyMasker;
        this.vectorService = vectorService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobPosition create(Long userId, JobDtos.PositionSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getPosition())) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "岗位名称不能为空");
        }
        JobPosition position = new JobPosition();
        position.setUserId(userId);
        position.setCompany(nvl(request.getCompany()));
        position.setPosition(request.getPosition().trim());
        position.setCity(nvl(request.getCity()));
        position.setSalaryRange(nvl(request.getSalaryRange()));
        position.setJobDesc(privacyMasker.mask(nvl(request.getJobDesc())));
        position.setRequirements(privacyMasker.mask(nvl(request.getRequirements())));
        position.setSourceUrl(nvl(request.getSourceUrl()));
        position.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        positionMapper.insert(position);
        // 写入 Milvus 向量库（Milvus 不可用时内部降级，不影响主流程）
        vectorService.indexPosition(position);
        log.info("创建岗位成功:userId={},positionId={},岗位={}", userId, position.getId(), position.getPosition());
        return position;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobPosition update(Long userId, Long id, JobDtos.PositionSaveRequest request) {
        JobPosition existing = requirePosition(userId, id);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR.getCode(), "请求体不能为空");
        }
        JobPosition update = new JobPosition();
        update.setId(existing.getId());
        update.setUserId(userId);
        update.setCompany(request.getCompany() == null ? existing.getCompany() : request.getCompany().trim());
        update.setPosition(StringUtils.hasText(request.getPosition()) ? request.getPosition().trim() : existing.getPosition());
        update.setCity(request.getCity() == null ? existing.getCity() : request.getCity().trim());
        update.setSalaryRange(request.getSalaryRange() == null ? existing.getSalaryRange() : request.getSalaryRange().trim());
        update.setJobDesc(request.getJobDesc() == null ? existing.getJobDesc() : privacyMasker.mask(request.getJobDesc().trim()));
        update.setRequirements(request.getRequirements() == null ? existing.getRequirements()
                : privacyMasker.mask(request.getRequirements().trim()));
        update.setSourceUrl(request.getSourceUrl() == null ? existing.getSourceUrl() : request.getSourceUrl().trim());
        update.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        int rows = positionMapper.update(update);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
        }
        JobPosition latest = positionMapper.selectById(id, userId);
        // JD 变化后重建向量索引
        vectorService.indexPosition(latest);
        return latest;
    }

    @Override
    public JobPosition detail(Long userId, Long id) {
        return requirePosition(userId, id);
    }

    @Override
    public ViewDtos.PageResult<JobPosition> page(Long userId, String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<JobPosition> list = positionMapper.selectByPage(userId, safeKeyword, offset, safeSize);
        int total = positionMapper.countByPage(userId, safeKeyword);
        ViewDtos.PageResult<JobPosition> result = ViewDtos.PageResult.of(list, total, safePage, safeSize);
        result.setKeyword(safeKeyword);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        int rows = positionMapper.softDelete(id, userId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
        }
        // 同步删除 Milvus 中该岗位的向量分块
        vectorService.deleteSource(JobVectorServiceImpl.SOURCE_POSITION, id);
        log.info("删除岗位:userId={},positionId={}", userId, id);
    }

    @Override
    public int countByUser(Long userId) {
        return positionMapper.countByUser(userId);
    }

    private JobPosition requirePosition(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
        }
        JobPosition position = positionMapper.selectById(id, userId);
        if (position == null) {
            throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
        }
        return position;
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }
}
