package org.zstack.test.integration.kvm.host

import org.zstack.core.db.DatabaseFacade
import org.zstack.kvm.KVMGlobalConfig
import org.zstack.sdk.CreateVmInstanceAction
import org.zstack.sdk.GetCpuMemoryCapacityAction
import org.zstack.sdk.UpdateVmInstanceAction
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.ImageSpec
import org.zstack.testlib.InstanceOfferingSpec
import org.zstack.testlib.L3NetworkSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit
import static java.util.Arrays.asList
/**
 * Created by camile on 2017/4/14.
 */


class HostCalculationMemoryCase extends SubCase {

    EnvSpec env

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(2)
                cpu = 2
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
                        name = "kvm1"
                        managementIp = "127.0.0.1"
                        username = "root"
                        password = "password"
                        totalCpu = 40
                        totalMem = SizeUnit.GIGABYTE.toByte(8)
                    }

                    kvm {
                        name = "kvm2"
                        managementIp = "127.0.0.2"
                        username = "root"
                        password = "password"
                        totalCpu = 40
                        totalMem = SizeUnit.GIGABYTE.toByte(8)
                    }
                    kvm {
                        name = "kvm3"
                        managementIp = "127.0.0.3"
                        username = "root"
                        password = "password"
                        totalCpu = 40
                        totalMem = SizeUnit.GIGABYTE.toByte(8)
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
                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }
                }
                attachBackupStorage("sftp")
            }
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void test() {
        env.create {
            KVMGlobalConfig.RESERVED_MEMORY_CAPACITY.updateValue("1G")
            testSetVmLargeMemoryFailure()
        }
    }


    void testSetVmLargeMemoryFailure() {
        InstanceOfferingSpec ioSpec = env.specByName("instanceOffering")
        ImageSpec iSpec = env.specByName("image1")
        L3NetworkSpec l3Spec = env.specByName("l3")
        GetCpuMemoryCapacityAction getCpuMemoryCapacityAction = new GetCpuMemoryCapacityAction();
        getCpuMemoryCapacityAction.all = true
        getCpuMemoryCapacityAction.sessionId = adminSession()
        GetCpuMemoryCapacityAction.Result res = getCpuMemoryCapacityAction.call()
        assert res.error == null
        assert res.value.totalMemory == SizeUnit.GIGABYTE.toByte(24 - 3)
        // Three Host of 8G memory ,reserved memory = 3*1 = 3
        CreateVmInstanceAction createVmInstanceAction = new CreateVmInstanceAction()
        createVmInstanceAction.name = "vm1"
        createVmInstanceAction.instanceOfferingUuid = ioSpec.inventory.uuid
        createVmInstanceAction.imageUuid = iSpec.inventory.uuid
        createVmInstanceAction.l3NetworkUuids = asList((l3Spec.inventory.uuid))
        createVmInstanceAction.sessionId = adminSession()
        CreateVmInstanceAction.Result createVmInstanceRes = createVmInstanceAction.call()
        assert createVmInstanceRes.error == null
        String vm1Uuid = createVmInstanceRes.value.inventory.uuid
        createVmInstanceAction.name = "vm2"
        createVmInstanceRes = createVmInstanceAction.call()
        assert createVmInstanceRes.error == null
        String vm2Uuid = createVmInstanceRes.value.inventory.uuid
        createVmInstanceAction.name = "vm3"
        createVmInstanceRes = createVmInstanceAction.call()
        assert createVmInstanceRes.error == null
        String vm3Uuid = createVmInstanceRes.value.inventory.uuid
        createVmInstanceAction.name = "vm4"
        createVmInstanceRes = createVmInstanceAction.call()
        assert createVmInstanceRes.error == null
        String vm4Uuid = createVmInstanceRes.value.inventory.uuid

        UpdateVmInstanceAction updateVmInstanceAction = new UpdateVmInstanceAction()
        updateVmInstanceAction.uuid = vm1Uuid
        updateVmInstanceAction.memorySize = SizeUnit.GIGABYTE.toByte(7)
        UpdateVmInstanceAction.Result updateVmRes = updateVmInstanceAction.call()
        assert updateVmRes.error == null
        updateVmInstanceAction.uuid = vm2Uuid
        updateVmRes = updateVmInstanceAction.call()
        assert updateVmRes.error == null
        updateVmInstanceAction.uuid = vm3Uuid
        updateVmRes = updateVmInstanceAction.call()
        assert updateVmRes.error == null
        updateVmInstanceAction.uuid = vm4Uuid
        updateVmRes = updateVmInstanceAction.call()
        assert updateVmRes.error != null
    }


}
