package com.mdframe.forge.starter.id.generator;


import com.mdframe.forge.starter.id.entity.WorkerNodePO;
import com.mdframe.forge.starter.id.service.IWorkNodePOAtomicService;
import com.xfvape.uid.utils.DockerUtils;
import com.xfvape.uid.utils.NetUtils;
import com.xfvape.uid.worker.WorkerIdAssigner;
import com.xfvape.uid.worker.WorkerNodeType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.RandomUtils;

import java.time.LocalDateTime;

/**
 * 覆盖实现WorkerIdAssigner  ，将worker分配给UidGenerator后将其丢弃
 *
 * @author haoxd
 */

@Slf4j
public class FusionDisposableWorkerIdAssigner implements WorkerIdAssigner {

    private static final int MIN_WORKER_BITS = 1;

    private static final int MAX_WORKER_BITS = 62;

    private final IWorkNodePOAtomicService workNodeService;

    private final int workerBits;

    private final long maxWorkerId;

    public FusionDisposableWorkerIdAssigner(IWorkNodePOAtomicService workNodeService, int workerBits) {
        if (workerBits < MIN_WORKER_BITS || workerBits > MAX_WORKER_BITS) {
            throw new IllegalArgumentException("workerBits 必须在 1 到 62 之间");
        }
        this.workNodeService = workNodeService;
        this.workerBits = workerBits;
        this.maxWorkerId = (1L << workerBits) - 1;
    }

    /**
     * Assign worker id base on database.<p>
     * If there is host name & port in the environment, we considered that the node runs in Docker container<br>
     * Otherwise, the node runs on an actual machine.
     *
     * @return assigned worker id
     */
    @Override
    public long assignWorkerId() {
        WorkerNodePO workNode = buildWorkerNode();
        workNodeService.addWorkerNode(workNode);
        Long workerId = workNode.getWorkNodeId();
        if (workerId == null || workerId <= 0) {
            throw new IllegalStateException("WorkerId 分配失败：数据库未返回有效自增 ID");
        }
        if (workerId > maxWorkerId) {
            throw new IllegalStateException("WorkerId 已超出 " + workerBits + " 位容量上限 " + maxWorkerId
                    + "，必须扩容位宽或迁移 ID 生成方案");
        }
        long warningThreshold = maxWorkerId - maxWorkerId / 5;
        if (workerId >= warningThreshold) {
            log.warn("WorkerId 容量接近耗尽: workerId={}, maxWorkerId={}, workerBits={}",
                    workerId, maxWorkerId, workerBits);
        }
        log.info("Worker node allocated: workerId={}, host={}, port={}",
                workerId, workNode.getHostName(), workNode.getPort());
        return workerId;
    }

    /**
     * Build worker node entity by IP and PORT
     */
    private WorkerNodePO buildWorkerNode() {
        WorkerNodePO workNode = new WorkerNodePO();
        if (DockerUtils.isDocker()) {
            workNode.setType(WorkerNodeType.CONTAINER.value());
            workNode.setHostName(DockerUtils.getDockerHost());
            workNode.setPort(DockerUtils.getDockerPort());
        } else {
            workNode.setType(WorkerNodeType.ACTUAL.value());
            workNode.setHostName(NetUtils.getLocalAddress());
            workNode.setPort(System.currentTimeMillis() + "-" + RandomUtils.nextInt(100000));
        }
        workNode.setLaunchDate(LocalDateTime.now());
        return workNode;
    }

}
