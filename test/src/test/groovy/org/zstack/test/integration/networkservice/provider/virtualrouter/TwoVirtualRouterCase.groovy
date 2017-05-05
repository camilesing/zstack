package org.zstack.test.integration.networkservice.provider.virtualrouter

import org.zstack.core.db.Q
import org.zstack.header.network.service.NetworkServiceType
import org.zstack.header.vm.VmInstance
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.network.service.eip.EipConstant
import org.zstack.network.service.lb.LoadBalancerConstants
import org.zstack.network.service.portforwarding.PortForwardingConstant
import org.zstack.network.service.virtualrouter.vyos.VyosConstants
import org.zstack.sdk.*
import org.zstack.test.integration.networkservice.provider.NetworkServiceProviderTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit
import static java.util.Arrays.asList

/**
 * Created by camile on 2017/5.
 * for bug 1968
 */
class TwoVirtualRouterCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(NetworkServiceProviderTest.springSpec)
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
                    name = "image"
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
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(12)
                    }

                    attachPrimaryStorage("local")
                    attachL2Network("PRIVATE-L2")
                    attachL2Network("PUBLIC-MANAGEMENT-L2")
                }
                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
                }
                l2NoVlanNetwork {
                    name = "PUBLIC-MANAGEMENT-L2"
                    physicalInterface = "eth0"
                    l3Network {
                        name = "PUBLIC-MANAGEMENT-L3"
                        ip {
                            startIp = "172.20.57.160"
                            endIp = "172.20.57.200"
                            gateway = "172.20.0.1"
                            netmask = "255.255.0.0"
                        }
                    }
                }

                l2VlanNetwork {
                    name = "PRIVATE-L2"
                    physicalInterface = "eth1"
                    vlan = 100
                    l3Network {
                        name = "PRIVATE-L3"
                        ip {
                            startIp = "10.10.2.100"
                            endIp = "10.20.2.200"
                            gateway = "10.10.2.1"
                            netmask = "255.0.0.0"
                        }
                        service {
                            provider = VyosConstants.VYOS_ROUTER_PROVIDER_TYPE
                            types = [NetworkServiceType.DHCP.toString(),
                                     NetworkServiceType.DNS.toString(),
                                     NetworkServiceType.SNAT.toString(),
                                     PortForwardingConstant.PORTFORWARDING_NETWORK_SERVICE_TYPE,
                                     LoadBalancerConstants.LB_NETWORK_SERVICE_TYPE_STRING,
                                     EipConstant.EIP_NETWORK_SERVICE_TYPE]
                        }
                    }
                }
                attachBackupStorage("sftp")

                virtualRouterOffering {
                    name = "vro"
                    memory = SizeUnit.MEGABYTE.toByte(512)
                    cpu = 2
                    useManagementL3Network("PUBLIC-MANAGEMENT-L3")
                    usePublicL3Network("PUBLIC-MANAGEMENT-L3")
                    useImage("vr")
                    isDefault = true
                }
            }
            vm {
                name = "vm1"
                useImage("image")
                useL3Networks("PRIVATE-L3")
                useInstanceOffering("instanceOffering")
            }
            vm {
                name = "vm2"
                useImage("image")
                useL3Networks("PRIVATE-L3")
                useInstanceOffering("instanceOffering")
            }
        }

    }

    @Override
    void test() {
        env.create {
            testStartVmWhenTwoVrExistAndDeleteTheFirstOne()
        }
    }

    void testStartVmWhenTwoVrExistAndDeleteTheFirstOne() {

        L3NetworkInventory publicL3 = env.inventoryByName("PUBLIC-MANAGEMENT-L3") as L3NetworkInventory
        VmInstanceInventory vm1 = env.inventoryByName("vm1") as VmInstanceInventory
        VmInstanceInventory vm2 = env.inventoryByName("vm2") as VmInstanceInventory
        VmNicInventory vm1Nic1 = vm1.vmNics.get(0)
        VmNicInventory vm2Nic1 = vm2.vmNics.get(0)

        //3 set EIP to vm1
        CreateVipAction createVipAction = new CreateVipAction()
        createVipAction.name = "vip"
        createVipAction.l3NetworkUuid = publicL3.uuid
        createVipAction.sessionId = adminSession()
        CreateVipAction.Result res = createVipAction.call()
        assert res.error == null
        VipInventory vipInventory = res.value.inventory

        CreateEipAction createEipAction = new CreateEipAction()
        createEipAction.name = "vm1_eip"
        createEipAction.vipUuid = vipInventory.uuid
        createEipAction.vmNicUuid = vm1Nic1.uuid
        createEipAction.sessionId = adminSession()
        CreateEipAction.Result eipRes = createEipAction.call()
        assert eipRes.error == null
        EipInventory eipInventory = eipRes.value.inventory

        //4 check vr ,mark be vr1
        String vr1Uuid = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid)
                .eq(VmInstanceVO_.type, "ApplianceVm")
                .findValue()
        assert vr1Uuid != null

        //5 create vip
        CreateVipAction createVipAction2 = new CreateVipAction()
        createVipAction2.name = "vip"
        createVipAction2.l3NetworkUuid = publicL3.uuid
        createVipAction2.sessionId = adminSession()
        CreateVipAction.Result creVipres = createVipAction2.call()
        assert creVipres.error == null
        VipInventory vipInventory2 = creVipres.value.inventory

        //6
        CreateLoadBalancerAction createLoadBalancerAction = new CreateLoadBalancerAction()
        createLoadBalancerAction.name = "carl"
        createLoadBalancerAction.vipUuid = vipInventory2.uuid
        createLoadBalancerAction.systemTags = asList("separateVirtualRouterVm")
        createLoadBalancerAction.sessionId = adminSession()
        CreateLoadBalancerAction.Result lbRes = createLoadBalancerAction.call()
        assert lbRes.error == null
        LoadBalancerInventory loadBalancerInventory = lbRes.value.inventory

        //7
        CreateLoadBalancerListenerAction createLoadBalancerListenerAction = new CreateLoadBalancerListenerAction()
        createLoadBalancerListenerAction.loadBalancerUuid = loadBalancerInventory.uuid
        createLoadBalancerListenerAction.loadBalancerPort = 22
        createLoadBalancerListenerAction.instancePort = 22
        createLoadBalancerListenerAction.name = "ssh"
        createLoadBalancerListenerAction.protocol = "tcp"
        createLoadBalancerListenerAction.sessionId = adminSession()
        CreateLoadBalancerListenerAction.Result lblRes =  createLoadBalancerListenerAction.call()
        assert lblRes.error == null
        LoadBalancerListenerInventory loadBalancerListenerInventory = lblRes.value.inventory

        //8
        AddVmNicToLoadBalancerAction addVmNicToLoadBalancerAction = new AddVmNicToLoadBalancerAction()
        addVmNicToLoadBalancerAction.listenerUuid = loadBalancerListenerInventory.uuid
        addVmNicToLoadBalancerAction.vmNicUuids = asList(vm1Nic1.uuid, vm2Nic1.uuid)
        addVmNicToLoadBalancerAction.sessionId = adminSession()
        AddVmNicToLoadBalancerAction.Result addvmNicToblRes = addVmNicToLoadBalancerAction.call()
        assert addvmNicToblRes.error == null
        LoadBalancerListenerInventory loadBalancerListenerInventory2 = addvmNicToblRes.value.inventory

        //9
        String vr2Uuid = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid)
                .eq(VmInstanceVO_.type, "ApplianceVm").notEq(VmInstanceVO_.uuid, vr1Uuid)
                .findValue()

        //10
        destroyVmInstance {
            uuid = vr1Uuid
        }
        //11
        stopVmInstance {
            uuid = vr2Uuid
        }
        //12
        stopVmInstance {
            uuid = vm1.uuid
        }
        stopVmInstance {
            uuid = vm2.uuid
        }
        //13
        startVmInstance {
            uuid = vm1.uuid
        }
        startVmInstance {
            uuid = vm2.uuid
        }
        //expect
        String newVr1Uuid = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid).
                eq(VmInstanceVO_.type,"ApplianceVm").notEq(VmInstanceVO_.uuid, vr2Uuid)
                .findValue()
        assert newVr1Uuid != null
    }
}
