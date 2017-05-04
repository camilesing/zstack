package org.zstack.test.integration.identity.quota

import org.zstack.core.db.Q
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.network.securitygroup.SecurityGroupConstant
import org.zstack.sdk.AccountInventory
import org.zstack.sdk.LogInByAccountAction
import org.zstack.sdk.SessionInventory
import org.zstack.test.integration.identity.IdentityTest
import org.zstack.testlib.*
import org.zstack.utils.data.SizeUnit
import static java.util.Arrays.asList

/**
 * Created by camile on 2017/5.
 */
class BatchStartVmWhenSetQuotaCase extends SubCase {
    EnvSpec env
    int limit = 2
    int vmCount = 3

    static SpringSpec springSpec = makeSpring {
        sftpBackupStorage()
        localStorage()
        virtualRouter()
        securityGroup()
        kvm()
    }

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(springSpec)
        useSpring(IdentityTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(1)
                cpu = 1
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
                        totalMem = SizeUnit.GIGABYTE.toByte(40)
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
            testBatchStartVmDontThanQuoteLimit(vmCount)
        }
    }
    void testBatchStartVmDontThanQuoteLimit(Integer vmCount){
        def thisImageUuid = (env.specByName("image1") as ImageSpec).inventory.uuid
        def _1CPU1G = (env.specByName("instanceOffering") as InstanceOfferingSpec).inventory.uuid
        def l3uuid = (env.specByName("pubL3") as L3NetworkSpec).inventory.uuid
        def threads = []

        AccountInventory testAccout = createAccount{
            name = "test"
            password ="password"
        }
        updateQuota {
            identityUuid = testAccout.uuid
            name = "vm.totalNum"
            value = limit
        }
        shareResource {
            resourceUuids = asList(thisImageUuid,_1CPU1G,l3uuid)
            toPublic = true
        }
        SessionInventory session = logInByAccount {
            accountName = "test"
            password = "password"
        }

        for (idx in 1..vmCount) {
            def thread = Thread.start {
                def vmName = "VM-${idx}".toString()
                try {
                    createVmInstance {
                        name = vmName
                        instanceOfferingUuid = _1CPU1G
                        imageUuid = thisImageUuid
                        l3NetworkUuids = [l3uuid]
                        sessionId = session.uuid
                    }
                } catch (AssertionError ignored) {

                }
            }
            threads.add(thread)
        }

        threads.each { it.join() }

//        for (int i = 0; i < vmCount; i++) {
//            createVmInstance {
//                def vmName = "VM"+i
//                name = vmName
//                instanceOfferingUuid = _1CPU1G
//                imageUuid = thisImageUuid
//                l3NetworkUuids = [l3uuid]
//                sessionId = session.uuid
//            }
//        }

        List<String> uuids = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).listValues()
        assert limit == uuids.size()

    }

}
