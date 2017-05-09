package org.zstack.test.integration.kvm.vm

import org.zstack.core.db.Q
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.ImageSpec
import org.zstack.testlib.InstanceOfferingSpec
import org.zstack.testlib.L3NetworkSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * Created by camile on 2017/5/9.
 */
class OneThousandVmBasicLifeCycleCase extends SubCase {
    EnvSpec env
    Long vmCount = 1000
    List<String> uuids


    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.MEGABYTE.toByte(512)
                cpu = 1
            }

            diskOffering {
                name = "diskOffering"
                diskSize = SizeUnit.GIGABYTE.toByte(1)
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"

                image {
                    name = "image1"
                    url = "http://zstack.org/download/test.qcow2"
                }

                image {
                    name = "vr"
                    url = "http://zstack.org/download/vr.qcow2"
                }
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
                        totalCpu = 200
                        totalMem = SizeUnit.GIGABYTE.toByte(640)

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
                        name = "pubL3"

                        ip {
                            startIp = "192.168.100.2"
                            endIp = "192.168.100.254"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                        ip {
                            startIp = "192.168.101.2"
                            endIp = "192.168.101.254"
                            netmask = "255.255.255.0"
                            gateway = "192.168.101.1"
                        }
                        ip {
                            startIp = "192.168.102.2"
                            endIp = "192.168.102.254"
                            netmask = "255.255.255.0"
                            gateway = "192.168.102.1"
                        }
                        ip {
                            startIp = "192.168.103.2"
                            endIp = "192.168.103.254"
                            netmask = "255.255.255.0"
                            gateway = "192.168.103.1"
                        }
                        ip {
                            startIp = "192.168.104.2"
                            endIp = "192.168.104.254"
                            netmask = "255.255.255.0"
                            gateway = "192.168.104.1"
                        }
                        ip {
                            startIp = "192.168.105.2"
                            endIp = "192.168.105.254"
                            netmask = "255.255.255.0"
                            gateway = "192.168.105.1"
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
            testCreateVm(vmCount)
            testStopVm()
            testStartVm()
            testRebootVm()
            testSetHA()
            testStopVm()
            testCancelHA()
            testStopVm()
            testStartVm()
            testRebootVm()
            testColdStopVm()
            testDestoryVm()
            testRecoverVm()
            testStartVm()
            testDestoryVm()
            testExpungeVm()
        }
    }

    void testCreateVm(Long num) {
        String thisImageUuid = (env.specByName("image1") as ImageSpec).inventory.uuid
        String _1CPU1G = (env.specByName("instanceOffering") as InstanceOfferingSpec).inventory.uuid
        String l3uuid = (env.specByName("pubL3") as L3NetworkSpec).inventory.uuid
        for (int i = 0; i < num; i++) {
            createVmInstance {
                def vmName = "VM" + i
                name = vmName
                instanceOfferingUuid = _1CPU1G
                imageUuid = thisImageUuid
                l3NetworkUuids = [l3uuid]
            }
        }
        uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).eq(VmInstanceVO_.state, VmInstanceState.Running).listValues()
        assert vmCount == uuids.size()
    }

    void testStopVm() {
        for (String vmUuid : uuids) {
            stopVmInstance {
                uuid = vmUuid
            }
        }
        uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).eq(VmInstanceVO_.state, VmInstanceState.Stopped).listValues()
        assert vmCount == uuids.size()
    }

    void testRebootVm() {
        for (String vmUuid : uuids) {
            rebootVmInstance {
                uuid = vmUuid
            }
        }
        uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).eq(VmInstanceVO_.state, VmInstanceState.Running).listValues()
        assert vmCount == uuids.size()
    }

    void testSetHA() {
        for (String vmUuid : uuids) {
            setVmInstanceHaLevel {
                uuid = vmUuid
                level = "NeverStop"
            }
        }
    }

    void testCancelHA() {
        for (String vmUuid : uuids) {
            deleteVmInstanceHaLevel {
                uuid = vmUuid
            }
        }
    }

    void testStartVm() {
        for (String vmUuid : uuids) {
            startVmInstance {
                uuid = vmUuid
            }
        }
        uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).eq(VmInstanceVO_.state, VmInstanceState.Running).listValues()
        assert vmCount == uuids.size()
    }

    void testColdStopVm() {
        for (String vmUuid : uuids) {
            stopVmInstance {
                type = "cold"
                uuid = vmUuid
            }
        }
        uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).eq(VmInstanceVO_.state, VmInstanceState.Stopped).listValues()
        assert vmCount == uuids.size()
    }

    void testDestoryVm() {
        for (String vmUuid : uuids) {
            stopVmInstance {
                type = "cold"
                uuid = vmUuid
            }
        }
        uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).eq(VmInstanceVO_.state, VmInstanceState.Destroyed).listValues()
        assert vmCount == uuids.size()
    }

    void testRecoverVm() {
        for (String vmUuid : uuids) {
            recoverVmInstance {
                uuid = vmUuid
            }
        }
        uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).eq(VmInstanceVO_.state, VmInstanceState.Stopped).listValues()
        assert vmCount == uuids.size()
    }

    void testExpungeVm() {
        for (String vmUuid : uuids) {
            expungeVmInstance {
                uuid = vmUuid
            }
        }
        uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).listValues()
        assert 0 == uuids.size()
    }

    @Override
    void clean() {
        env.delete()
    }
}
