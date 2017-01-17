package org.zstack.header.storage.backup

import org.zstack.header.storage.backup.APIAttachBackupStorageToZoneEvent

doc {
    title "AttachBackupStorageToZone"

    category "storage.backup"

    desc "在这里填写API描述"

    rest {
        request {
			url "POST /v1/zones/{zoneUuid}/backup-storage/{backupStorageUuid}"


            header (OAuth: 'the-session-uuid')

            clz APIAttachBackupStorageToZoneMsg.class

            desc ""
            
			params {

				column {
					name "zoneUuid"
					enclosedIn ""
					desc "区域UUID"
					location "url"
					type "String"
					optional false
					since "0.6"
					
				}
				column {
					name "backupStorageUuid"
					enclosedIn ""
					desc "镜像存储UUID"
					location "url"
					type "String"
					optional false
					since "0.6"
					
				}
				column {
					name "systemTags"
 					enclosedIn ""
 					desc ""
					location "body"
					type "List"
					optional true
					since "0.6"
					
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "0.6"
					
				}
			}
        }

        response {
            clz APIAttachBackupStorageToZoneEvent.class
        }
    }
}