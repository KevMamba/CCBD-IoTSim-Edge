package org.edge.core;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.edge.core.edge.EdgeDevice;
import org.edge.core.feature.Mobility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// Maximize sum of inverse distance and number of free pes

/**
 * VmAllocationPolicyEdge is a VmAllocationPolicy that chooses, as the host for a VM, the host
 * with less PEs in use and closest to the EdgeDevice.
 *
 */
public class VmAllocationPolicyEdge extends VmAllocationPolicy {

    /** The vm table. */
    private Map<String, Host> vmTable;

    /** The used pes. */
    private Map<String, Integer> usedPes;

    /** The distances between host and edge device. */
    private List<Double> distTable;

    /** The free pes. */
    private List<Integer> freePes;

    /** priority assigned to distance. */
    public float distPrio;

    /** priority assigned to pes. */
    public float pePrio;

    /**
     * Creates the new VmAllocationPolicySimple object.
     *
     * @param list the list
     * @param distPrio priority assigned to distance between the host and the IoT Device
     * @param pePrio priority assigned to number of free PEs
     */
    public VmAllocationPolicyEdge(List<? extends Host> list, float distPrio, float pePrio, Mobility.Location avgLocation) {
        super(list);

        setFreePes(new ArrayList<Integer>());
        for (Host host : getHostList()) {
            getFreePes().add(host.getNumberOfPes());
        }
        this.distPrio = distPrio;
        this.pePrio = pePrio;

        setVmTable(new HashMap<String, Host>());
        setUsedPes(new HashMap<String, Integer>());
        setDistance(new ArrayList<Double>());
        calcDistance(list, avgLocation);
    }

    private void calcDistance(List<? extends Host> list, Mobility.Location avgLocation) {
        for (Host host : list) {
            EdgeDevice edge = (EdgeDevice) host;
            Mobility.Location edgeLocation = edge.getLocation().location;
            double distance = Math.sqrt(Math.pow(edgeLocation.x - avgLocation.x,2) +
                    Math.pow(edgeLocation.y - avgLocation.y,2) +
                    Math.pow(edgeLocation.z - avgLocation.z,2));
            System.out.println("The distance and inverse: " + distance + "    " +  1/distance);
            getDistance().add(1/distance); //inverse distance
        }
    }

    /**
     * Allocates a host for a given VM.
     *
     * @param vm VM specification
     * @return $true if the host could be allocated; $false otherwise
     * @pre $none
     * @post $none
     */
    @Override
    public boolean allocateHostForVm(Vm vm) {
        int requiredPes = vm.getNumberOfPes();
        boolean result = false;
        int tries = 0;
        List<Integer> freePesTmp = new ArrayList<Integer>();
        for (Integer freePes : getFreePes()) {
            freePesTmp.add(freePes);
        }
        List<Double>  distanceTmp= new ArrayList<>();
        for (Double distance : getDistance()) {
            distanceTmp.add(distance);
        }

        if (!getVmTable().containsKey(vm.getUid())) { // if this vm was not created
            do {// we still trying until we find a host or until we try all of them
                double moreFree = Double.MIN_VALUE;
                int idx = -1;

                // we want the host with less pes in use
                for (int i = 0; i < freePesTmp.size(); i++) {
                    //if (freePesTmp.get(i) > moreFree) {
                    if ( ((pePrio*freePesTmp.get(i)) + (distPrio*distanceTmp.get(i))) > moreFree) {
                        moreFree = ((pePrio*freePesTmp.get(i)) + (distPrio*distanceTmp.get(i)));
                        idx = i;
                    }
                }

                Host host = getHostList().get(idx);
                result = host.vmCreate(vm);

                if (result) { // if vm were succesfully created in the host
                    getVmTable().put(vm.getUid(), host);
                    getUsedPes().put(vm.getUid(), requiredPes);
                    getFreePes().set(idx, getFreePes().get(idx) - requiredPes);
                    result = true;
                    break;
                } else {
                    freePesTmp.set(idx, Integer.MIN_VALUE);
                }
                tries++;
            } while (!result && tries < getFreePes().size());

        }

        return result;
    }

    /**
     * Releases the host used by a VM.
     *
     * @param vm the vm
     * @pre $none
     * @post none
     */
    @Override
    public void deallocateHostForVm(Vm vm) {
        Host host = getVmTable().remove(vm.getUid());
        int idx = getHostList().indexOf(host);
        int pes = getUsedPes().remove(vm.getUid());
        if (host != null) {
            host.vmDestroy(vm);
            getFreePes().set(idx, getFreePes().get(idx) + pes);
        }
    }

    /**
     * Gets the host that is executing the given VM belonging to the given user.
     *
     * @param vm the vm
     * @return the Host with the given vmID and userID; $null if not found
     * @pre $none
     * @post $none
     */
    @Override
    public Host getHost(Vm vm) {
        return getVmTable().get(vm.getUid());
    }

    /**
     * Gets the host that is executing the given VM belonging to the given user.
     *
     * @param vmId the vm id
     * @param userId the user id
     * @return the Host with the given vmID and userID; $null if not found
     * @pre $none
     * @post $none
     */
    @Override
    public Host getHost(int vmId, int userId) {
        return getVmTable().get(Vm.getUid(userId, vmId));
    }

    /**
     * Gets the vm table.
     *
     * @return the vm table
     */
    public Map<String, Host> getVmTable() {
        return vmTable;
    }

    /**
     * Sets the vm table.
     *
     * @param vmTable the vm table
     */
    protected void setVmTable(Map<String, Host> vmTable) {
        this.vmTable = vmTable;
    }

    /**
     * Sets the distance table.
     *
     * @param distTable the vm table
     */
    private void setDistance(ArrayList<Double> distTable) {
        this.distTable = distTable;
    }

    /**
     * Gets the used pes.
     *
     * @return the used pes
     */
    protected List<Double> getDistance() {
        return distTable;
    }

    /**
     * Gets the used pes.
     *
     * @return the used pes
     */
    protected Map<String, Integer> getUsedPes() {
        return usedPes;
    }

    /**
     * Sets the used pes.
     *
     * @param usedPes the used pes
     */
    protected void setUsedPes(Map<String, Integer> usedPes) {
        this.usedPes = usedPes;
    }

    /**
     * Gets the free pes.
     *
     * @return the free pes
     */
    protected List<Integer> getFreePes() {
        return freePes;
    }

    /**
     * Sets the free pes.
     *
     * @param freePes the new free pes
     */
    protected void setFreePes(List<Integer> freePes) {
        this.freePes = freePes;
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.VmAllocationPolicy#optimizeAllocation(double, cloudsim.VmList, double)
     */
    @Override
    public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * @see org.cloudbus.cloudsim.VmAllocationPolicy#allocateHostForVm(org.cloudbus.cloudsim.Vm,
     * org.cloudbus.cloudsim.Host)
     */
    @Override
    public boolean allocateHostForVm(Vm vm, Host host) {
        if (host.vmCreate(vm)) { // if vm has been succesfully created in the host
            getVmTable().put(vm.getUid(), host);

            int requiredPes = vm.getNumberOfPes();
            int idx = getHostList().indexOf(host);
            getUsedPes().put(vm.getUid(), requiredPes);
            getFreePes().set(idx, getFreePes().get(idx) - requiredPes);

            Log.formatLine(
                    "%.2f: VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),
                    CloudSim.clock());
            return true;
        }

        return false;
    }
}
