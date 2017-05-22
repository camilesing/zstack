package org.zstack.test.integration.storage.primary.local

import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.header.image.ImageConstant
import org.zstack.header.image.ImagePlatform
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.network.securitygroup.SecurityGroupConstant
import org.zstack.sdk.DiskOfferingInventory
import org.zstack.storage.primary.local.LocalStorageHostRefVO
import org.zstack.test.integration.kvm.hostallocator.AllocatorTest
import org.zstack.testlib.*
import org.zstack.utils.data.SizeUnit

/**
 * Created by camile on 2017/5.
 */
class CreateVmsInBatchWithlargeDataVolumeCase extends SubCase {
    EnvSpec env
    def correctCount = 30

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(AllocatorTest.springSpec)
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
                name = "rootOffering-50G"
                diskSize = SizeUnit.GIGABYTE.toByte(50)
            }
            diskOffering {
                name = "diskOffering-1T"
                diskSize = SizeUnit.GIGABYTE.toByte(1024)
            }
            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"

                image {
                    name = "image1"
                    url  = "http://zstack.org/download/test2.iso"
                    platform = ImagePlatform.Linux.toString()
                    mediaType = ImageConstant.ImageMediaType.RootVolumeTemplate.toString()
                    format = "iso"
                }
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm1"
                        managementIp = "127.0.0.2"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    kvm {
                        name = "kvm2"
                        managementIp = "127.0.0.3"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    kvm {
                        name = "kvm3"
                        managementIp = "127.0.0.4"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    kvm {
                        name = "kvm4"
                        managementIp = "127.0.0.5"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    kvm {
                        name = "kvm5"
                        managementIp = "127.0.0.6"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    kvm {
                        name = "kvm6"
                        managementIp = "127.0.0.7"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    kvm {
                        name = "kvm7"
                        managementIp = "127.0.0.8"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    kvm {
                        name = "kvm8"
                        managementIp = "127.0.0.9"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    kvm {
                        name = "kvm9"
                        managementIp = "127.0.0.10"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(10)
                    }
                    attachPrimaryStorage("local")
                    attachL2Network("l2")
                }

                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        service {
                            provider = SecurityGroupConstant.SECURITY_GROUP_PROVIDER_TYPE
                            types = [SecurityGroupConstant.SECURITY_GROUP_NETWORK_SERVICE_TYPE]
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
                            startIp = "12.16.10.10"
                            endIp = "12.16.10.100"
                            netmask = "255.255.255.0"
                            gateway = "12.16.10.1"
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
            testConcurrentCreateVMs(correctCount)
        }
    }

    void testConcurrentCreateVMs(Long num) {
        DatabaseFacade dbf = bean(DatabaseFacade.class)
        List<LocalStorageHostRefVO> up_vos = new ArrayList<>()
        List<LocalStorageHostRefVO> vos = Q.New(LocalStorageHostRefVO.class).list()
        for (LocalStorageHostRefVO vo : vos) {
            vo.totalCapacity =  SizeUnit.GIGABYTE.toByte(7168) //7T
            vo.availableCapacity = SizeUnit.GIGABYTE.toByte(7168)
            vo.totalPhysicalCapacity = SizeUnit.GIGABYTE.toByte(7168)
            vo.availablePhysicalCapacity = SizeUnit.GIGABYTE.toByte(7168)
            up_vos.add(vo)
        }
        dbf.updateCollection(up_vos)


        def thisImageUuid = (env.specByName("image1") as ImageSpec).inventory.uuid
        def _1CPU1G = (env.specByName("instanceOffering") as InstanceOfferingSpec).inventory.uuid
        def l3uuid = (env.specByName("pubL3") as L3NetworkSpec).inventory.uuid
        DiskOfferingInventory root_50G_vo = env.inventoryByName("rootOffering-50G")
        DiskOfferingInventory data_50G_vo = env.inventoryByName("diskOffering-1T")

        def threads = []
        1.upto(num, {
            def vmName = "VM-${it}".toString()
            def thread = Thread.start {
                createVmInstance {
                    name = vmName
                    instanceOfferingUuid = _1CPU1G
                    imageUuid = thisImageUuid
                    l3NetworkUuids = [l3uuid]
                    rootDiskOfferingUuid = root_50G_vo.uuid
                    dataDiskOfferingUuids = [data_50G_vo.uuid]
                }
            }
            threads.add(thread)
        })

        threads.each { it.join() }

        assert correctCount  == Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).eq(VmInstanceVO_.state, VmInstanceState.Running).listValues().size()


    }
}