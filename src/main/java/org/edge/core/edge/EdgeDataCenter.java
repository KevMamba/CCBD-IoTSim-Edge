package org.edge.core.edge;

import java.util.List;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.edge.core.CloudletSchedulerTimeSharedEdge;
import org.edge.core.feature.EdgeLet;
import org.edge.core.feature.EdgeState;
import org.edge.core.iot.IoTDevice;
import org.edge.entity.ConnectionHeader;
import org.edge.entity.DevicesInfo;
import org.edge.protocol.CommunicationProtocol;
import org.edge.utils.LogUtil;


/**
 * this is edge data center extended from Datacenter
 * in this Datacenter, it has got its own edgeDatacenterCharacteristics
 * @author cody
 *
 */
public class EdgeDataCenter extends Datacenter {

	private EdgeDatacenterCharacteristics characteristics;

	public EdgeDatacenterCharacteristics getEdgeCharacteristics() {
		return this.characteristics;
	}

	public void setEdgeCharacteristics(EdgeDatacenterCharacteristics characteristics) {
		this.characteristics = characteristics;
	}

	public EdgeDataCenter(String name, EdgeDatacenterCharacteristics characteristics,
			VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval)
					throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
		this.characteristics = characteristics;
	}

	@Override
	protected void processVmCreate(SimEvent ev, boolean ack) {
		Vm vm = (Vm) ev.getData();

		boolean result = getVmAllocationPolicy().
				allocateHostForVm(vm);

		if (ack) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = vm.getId();

			if (result) {
				data[2] = CloudSimTags.TRUE;
			} else {
				data[2] = CloudSimTags.FALSE;
			}
			send(vm.getUserId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_CREATE_ACK, data);
		}

		if (result) {
			getVmList().add(vm);

			if (vm.isBeingInstantiated()) {
				vm.setBeingInstantiated(false);
			}

			vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler()
					.getAllocatedMipsForVm(vm));
		}

	}

	@Override
	protected void processCloudletSubmit(SimEvent ev, boolean ack) {
		updateCloudletProcessing();

		try {
			// gets the Cloudlet object
			EdgeLet cl = (EdgeLet) ev.getData();

			// checks whether this Cloudlet has finished or not
			if (cl.isFinished()) {
				String name = CloudSim.getEntityName(cl.getUserId());
				Log.printLine(getName() + ": Warning - Cloudlet #" + cl.getCloudletId() + " owned by " + name
						+ " is already completed/finished.");
				Log.printLine("Therefore, it is not being executed again");
				Log.printLine();

				// NOTE: If a Cloudlet has finished, then it won't be processed.
				// So, if ack is required, this method sends back a result.
				// If ack is not required, this method don't send back a result.
				// Hence, this might cause CloudSim to be hanged since waiting
				// for this Cloudlet back.
				if (ack) {
					int[] data = new int[3];
					data[0] = getId();
					data[1] = cl.getCloudletId();
					data[2] = CloudSimTags.FALSE;

					// unique tag = operation tag
					int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
					sendNow(cl.getUserId(), tag, data);
				}

				sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);

				return;
			}

			// process this Cloudlet to this CloudResource
			cl.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(), getCharacteristics()
					.getCostPerBw());

			int userId = cl.getUserId();
			int vmId = cl.getVmId();

			// time to transfer the files
			double fileTransferTime = predictFileTransferTime(cl.getRequiredFiles());

			Host host = getVmAllocationPolicy().getHost(vmId, userId);
			Vm vm = host.getVm(vmId, userId);
			CloudletSchedulerTimeSharedEdge scheduler = (CloudletSchedulerTimeSharedEdge) vm.getCloudletScheduler();

			double estimatedFinishTime = scheduler.cloudletSubmit(cl, fileTransferTime);

			// if this cloudlet is in the exec queue
			if (estimatedFinishTime > 0.0 && !Double.isInfinite(estimatedFinishTime)) {
				estimatedFinishTime += fileTransferTime;
				send(getId(), estimatedFinishTime, CloudSimTags.VM_DATACENTER_EVENT);
			}

			if (ack) {
				int[] data = new int[3];
				data[0] = getId();
				data[1] = cl.getCloudletId();
				data[2] = CloudSimTags.TRUE;

				// unique tag = operation tag
				int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
				sendNow(cl.getUserId(), tag, data);
			}
		} catch (ClassCastException c) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "ClassCastException error.");
			c.printStackTrace();
		} catch (Exception e) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "Exception error.");
			e.printStackTrace();
		}

		checkCloudletCompletion();
	}

	@Override
	public void processEvent(SimEvent ev) {
		// TODO Auto-generated method stub

		super.processEvent(ev);
	}

	@Override
	public void processOtherEvent(SimEvent ev) {
		// TODO Auto-generated method stub
		int tag = ev.getTag();
		switch (tag) {
		case EdgeState.REQUEST_CONNECTION:
			this.processConnectionRequest(ev);

			break;
		case EdgeState.LOST_CONNECTION:
			this.processConnectionLost(ev);
			break;

		default:
			break;
		}

	}

	private void processConnectionLost(SimEvent ev) {
		ConnectionHeader connectionInfo = (ConnectionHeader) ev.getData();
		List<Host> hostList = this.characteristics.getHostList();
		for (Host host : hostList) {
			List<Vm> vmList2 = host.getVmList();
			for (Vm vm : vmList2) {
				if (vm.getId() == connectionInfo.vmId) {
					EdgeDevice device=(EdgeDevice) host;
					device.removeConnection(connectionInfo);
					break;
				}
			}
		}

	}
	/**
	 * set up connection between edge devices in this data center and iot devices
	 * @param ev its data contains connection information between edge device and iot device
	 */
	private void processConnectionRequest(SimEvent ev) {
		//		LogUtil.info(CloudSim.clock());
		ConnectionHeader info = (ConnectionHeader) ev.getData();
		Class<? extends CommunicationProtocol>[] supported_comm_protocols_for_IoT = this.getEdgeCharacteristics()
				.getCommunicationProtocolSupported();
		Class<? extends IoTDevice>[] ioTDeviceSupported_for_IoT = this.getEdgeCharacteristics().getIoTDeviceSupported();

		boolean supportDevice = false;
		for (Class ioT : ioTDeviceSupported_for_IoT) {
			// if this edge device supports this ioTdevice
			if (ioT.equals(info.ioTDeviceType)) {
				supportDevice = true;
				// find the suitable protocol
				for (Class<? extends CommunicationProtocol> clzz : supported_comm_protocols_for_IoT) {
					//
					if (clzz.equals(info.communicationProtocolForIoT)
							/* || clzz.isAssignableFrom(device.getClass()) */) {
						List<EdgeDevice> hostList = this.characteristics.getHostList();

						for (EdgeDevice edgeDevice : hostList) {
							List<Vm> vmList = edgeDevice.getVmList();
							for (Vm vm : vmList) {
								if (vm.getId() == info.vmId) {
									boolean connect_IoT_device = edgeDevice.connect_IoT_device(info);
									if (connect_IoT_device) {
										info.state = EdgeState.SUCCESS;
										// connection infor is from vm part
										info.sourceId = info.vmId;
										this.send(info.brokeId, this.getNeworkDelay(new DevicesInfo(info.ioTId, info.vmId)),
												EdgeState.CONNECTING_ACK, info);
									}
									return;
								}
							}

						}
						return;

					}
				}

			}
		}
		if (supportDevice) {
			this.schedule(info.ioTId, this.getNeworkDelay(new DevicesInfo(info.ioTId, info.vmId)), EdgeState.DISCONNECTED,EdgeState.UNSUPPORTED_COMMUNICATION_PROTOCOL);
			LogUtil.info("EdgeDataCenter: the edgeDevice cannot support protocol "
					+ info.communicationProtocolForIoT.getSimpleName() + "for ioTdevice " + info.ioTId);
		} else {
			this.schedule(info.ioTId, this.getNeworkDelay(new DevicesInfo(info.ioTId, info.vmId)), EdgeState.DISCONNECTED,EdgeState.UNSUPPORTED_IOT_DEVICE);
			LogUtil.info("EdgeDataCenter: the edgeDevice cannot support IoT device "
					+ info.ioTDeviceType.getSimpleName());
		}

		info.state = EdgeState.FAILURE;
		this.send(info.brokeId, this.getNeworkDelay(new DevicesInfo(info.ioTId, info.vmId)), EdgeState.CONNECTING_ACK, info);
	}

	/**
	 * get network delay between edge device and iot device.
	 * for now, it simply return 0
	 * @param devicesInfo 
	 */
	private double getNeworkDelay(DevicesInfo devicesInfo) {
		return 0;
	}
}