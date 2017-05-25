package org.zstack.test.integration.image

import org.springframework.http.HttpEntity
import org.zstack.core.db.Q
import org.zstack.header.image.ImageVO
import org.zstack.header.image.ImageVO_
import org.zstack.header.network.service.NetworkServiceType
import org.zstack.network.service.eip.EipConstant
import org.zstack.network.service.flat.FlatNetworkServiceConstant
import org.zstack.network.service.userdata.UserdataConstant
import org.zstack.sdk.*
import org.zstack.storage.backup.sftp.SftpBackupStorageCommands
import org.zstack.storage.backup.sftp.SftpBackupStorageConstant
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.BackupStorageSpec
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

import static java.util.Arrays.asList

/**
 * Created by Camile on 2017/5
 */
class AddImageWhenSetQuotaCase extends SubCase {
    EnvSpec env
    def testAccoutUuid

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(1)
                cpu = 1
            }

            diskOffering {
                name = "diskOffering-10G"
                diskSize = SizeUnit.GIGABYTE.toByte(20)
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("local")
                    attachL2Network("l2")
                }

                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
                    availableCapacity = SizeUnit.GIGABYTE.toByte(60)
                    totalCapacity = SizeUnit.GIGABYTE.toByte(60)
                }


                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        service {
                            provider = FlatNetworkServiceConstant.FLAT_NETWORK_SERVICE_TYPE_STRING
                            types = [NetworkServiceType.DHCP.toString(), EipConstant.EIP_NETWORK_SERVICE_TYPE, UserdataConstant.USERDATA_TYPE_STRING]
                        }

                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }

                    l3Network {
                        name = "pubL3"

                        ip {
                            startIp = "11.168.100.10"
                            endIp = "11.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "11.168.100.1"
                        }
                    }
                }

                attachBackupStorage("sftp")
            }
        }
    }

    @Override
    void test() {
        env.create {
            testOrderAddImageFailure()
            testConcurrentAddImageFailure()
        }
    }

    void testOrderAddImageFailure() {
        BackupStorageInventory bs = env.inventoryByName("sftp")
        PrimaryStorageInventory ps = env.inventoryByName("local")
        InstanceOfferingInventory instanceOffering = env.inventoryByName("instanceOffering")
        L3NetworkInventory l3 = env.inventoryByName("l3")
        DiskOfferingInventory doIvo = env.inventoryByName("diskOffering-10G")

        AccountInventory testAccout = createAccount {
            name = "test"
            password = "password"
        }

        testAccoutUuid = testAccout.uuid

        updateQuota {
            identityUuid = testAccout.uuid
            name = "image.size"
            value = SizeUnit.GIGABYTE.toByte(2)
        }

        env.simulator(SftpBackupStorageConstant.DOWNLOAD_IMAGE_PATH) { HttpEntity<String> e, EnvSpec spec ->
            def cmd = JSONObjectUtil.toObject(e.getBody(), SftpBackupStorageCommands.DownloadCmd.class)
            BackupStorageSpec bsSpec = spec.specByUuid(cmd.uuid)

            def rsp = new SftpBackupStorageCommands.DownloadResponse()
            rsp.size = SizeUnit.GIGABYTE.toByte(2)
            rsp.actualSize = SizeUnit.GIGABYTE.toByte(1)
            rsp.availableCapacity = bsSpec.availableCapacity - SizeUnit.GIGABYTE.toByte(1)
            rsp.totalCapacity = bsSpec.totalCapacity
            rsp.success = true
            return rsp
        }

        env.simulator(SftpBackupStorageConstant.GET_IMAGE_SIZE) { HttpEntity<String> e, EnvSpec spec ->
            def rep = new SftpBackupStorageCommands.GetImageSizeRsp()
            rep.size = SizeUnit.GIGABYTE.toByte(1)
            rep.success = true
            return rep
        }

        SessionInventory sessionInventory = logInByAccount {
            accountName = "test"
            password = "password"
        }

        AddImageAction addImageAction = new AddImageAction()
        addImageAction.backupStorageUuids = asList(bs.uuid)
        addImageAction.name = "1G"
        addImageAction.url = "file:///download/some-site/static/image.iso"
        addImageAction.format = "iso"
        addImageAction.sessionId = sessionInventory.uuid
        AddImageAction.Result res = addImageAction.call()
        assert res.error == null
        AddImageAction.Result res2 = addImageAction.call()
        assert res2.error == null
        AddImageAction.Result res3 = addImageAction.call()
        assert res3.error != null
        Long count = Q.New(ImageVO.class).count()
        assert count == 2
        deleteImage{
            uuid = res.value.inventory.uuid
        }
        deleteImage{
            uuid = res2.value.inventory.uuid
        }
        expungeImage{
            imageUuid = res.value.inventory.uuid
            backupStorageUuids = asList(bs.uuid)
        }
        expungeImage{
            imageUuid = res2.value.inventory.uuid
            backupStorageUuids = asList(bs.uuid)
        }
        count = Q.New(ImageVO.class).count()
        assert count == 0

    }

    void testConcurrentAddImageFailure() {
        BackupStorageInventory bs = env.inventoryByName("sftp")

        def threads = []
        1.upto(3, {
            def imageName = "Image-${it}".toString()
            def thread = Thread.start {
                addImage {
                    backupStorageUuids =  asList(bs.uuid)
                    name = imageName
                    url = "file:///download/some-site/static/image.iso"
                    format = "iso"
                }
            }
            threads.add(thread)
        })

        threads.each { it.join() }

        Long count = Q.New(ImageVO.class).count()
        assert count == 2
    }


}
