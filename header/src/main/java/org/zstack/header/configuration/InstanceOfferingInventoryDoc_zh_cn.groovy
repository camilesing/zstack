package org.zstack.header.configuration

doc {

    title "云主机规格清单"

    field {
        name "uuid"
        desc "资源的UUID，唯一标示该资源"
        type "String"
        since "0.6"
    }
    field {
        name "name"
        desc "资源名称"
        type "String"
        since "0.6"
    }
    field {
        name "description"
        desc "资源的详细描述"
        type "String"
        since "0.6"
    }
    field {
        name "cpuNum"
        desc "CPU数量"
        type "Integer"
        since "0.6"
    }
    field {
        name "cpuSpeed"
        desc "CPU速度"
        type "Integer"
        since "0.6"
    }
    field {
        name "memorySize"
        desc "内存大小"
        type "Long"
        since "0.6"
    }
    field {
        name "type"
        desc "类型"
        type "String"
        since "0.6"
    }
    field {
        name "allocatorStrategy"
        desc "分配策略"
        type "String"
        since "0.6"
    }
    field {
        name "sortKey"
        desc ""
        type "Integer"
        since "0.6"
    }
    field {
        name "createDate"
        desc "创建时间"
        type "Timestamp"
        since "0.6"
    }
    field {
        name "lastOpDate"
        desc "最后一次修改时间"
        type "Timestamp"
        since "0.6"
    }
    field {
        name "state"
        desc "状态（启用，禁用）"
        type "String"
        since "0.6"
    }
}
