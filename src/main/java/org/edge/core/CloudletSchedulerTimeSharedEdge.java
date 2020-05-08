/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.edge.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.edge.core.feature.EdgeLet;

import static java.lang.System.exit;

/**
 * CloudletSchedulerTimeShared implements a policy of scheduling performed by a virtual machine.
 * Cloudlets execute time-shared in VM.
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class CloudletSchedulerTimeSharedEdge extends CloudletScheduler {

    /** The cloudlet exec list. */
    private List<? extends ResEdgeLet> cloudletExecList;

    /** The cloudlet paused list. */
    private List<? extends ResEdgeLet> cloudletPausedList;

    /** The cloudlet finished list. */
    private List<? extends ResEdgeLet> cloudletFinishedList;

    private List<? extends ResCloudlet> dummyList;

    private Map<String, Integer> sensorMap;

    /** The current cp us. */
    protected int currentCPUs;

    /**
     * Creates a new CloudletSchedulerTimeShared object. This method must be invoked before starting
     * the actual simulation.
     *
     * @pre $none
     * @post $none
     */
    public CloudletSchedulerTimeSharedEdge() {
        super();
        cloudletExecList = new ArrayList<ResEdgeLet>();
        cloudletPausedList = new ArrayList<ResEdgeLet>();
        cloudletFinishedList = new ArrayList<ResEdgeLet>();
        currentCPUs = 0;
    }

    public void setSensorMap(Map<String, Integer> sensorMap) {
        this.sensorMap = sensorMap;
    }

    public Map<String, Integer> getSensorMap() {
        return sensorMap;
    }
    /**
     * Updates the processing of cloudlets running under management of this scheduler.
     *
     * @param currentTime current simulation time
     * @param mipsShare array with MIPS share of each processor available to the scheduler
     * @return time predicted completion time of the earliest finishing cloudlet, or 0 if there is
     *         no next events
     * @pre currentTime >= 0
     * @post $none
     */
    @Override
    public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
        setCurrentMipsShare(mipsShare);
        double timeSpam = currentTime - getPreviousTime();

        for (ResEdgeLet rcl : getCloudletExecList()) {
            //System.out.println("Type of sensor" + rcl.edgelet.sensorType + "-- the capacity :" + getCapacity(mipsShare));
            //System.out.println("The product shoule be 10000 and is actually ; " + (sensorMap.get(rcl.edgelet.sensorType) * getCapacity(mipsShare)));
            /*if(getCloudletExecList().size() > 1) {
                System.out.println("\nsensor type:" + rcl.edgelet.sensorType);
                System.out.println("capacity:"+getCapacity(mipsShare)+" timespan "+timeSpam);
                System.out.println("sensorweight:"+sensorMap.get(rcl.edgelet.sensorType)+ " cloudlets in list:" + getCloudletExecList().size());
                System.out.println("Cloudlet before updating:" + rcl.getRemainingCloudletLength());
                System.out.println("Updated amount:" + (getCapacity(mipsShare) * timeSpam * rcl.getNumberOfPes() * sensorMap.get(rcl.edgelet.sensorType) * Consts.MILLION));
            }*/
            rcl.updateCloudletFinishedSoFar((long) (getCapacity(mipsShare) * timeSpam * rcl.getNumberOfPes() * sensorMap.get(rcl.edgelet.sensorType) * Consts.MILLION));
        }

        if (getCloudletExecList().size() == 0) {
            setPreviousTime(currentTime);
            return 0.0;
        }

        // check finished cloudlets
        double nextEvent = Double.MAX_VALUE;
        List<ResEdgeLet> toRemove = new ArrayList<ResEdgeLet>();
        for (ResEdgeLet rcl : getCloudletExecList()) {
            long remainingLength = rcl.getRemainingCloudletLength();
            if (remainingLength == 0) {// finished: remove from the list
                toRemove.add(rcl);
                cloudletFinish(rcl);
            }
        }
        getCloudletExecList().removeAll(toRemove);

        // estimate finish time of cloudlets
        for (ResEdgeLet rcl : getCloudletExecList()) {
            double estimatedFinishTime = currentTime
                    + (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
            if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
                estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
            }

            if (estimatedFinishTime < nextEvent) {
                nextEvent = estimatedFinishTime;
            }
        }

        setPreviousTime(currentTime);
        return nextEvent;
    }

    /**
     * Gets the capacity.
     *
     * @param mipsShare the mips share
     * @return the capacity
     */
    protected double getCapacity(List<Double> mipsShare) {
        double capacity = 0.0;
        int cpus = 0;
        for (Double mips : mipsShare) {
            capacity += mips;
            if (mips > 0.0) {
                cpus++;
            }
        }
        currentCPUs = cpus;

        int pesInUse = 0;
        for (ResEdgeLet rcl : getCloudletExecList()) {
            pesInUse += rcl.getNumberOfPes();
        }

        int sensorweight = 0;
        for (ResEdgeLet rcl : getCloudletExecList()) {
            if(sensorMap.get(rcl.edgelet.sensorType) != null) {
                sensorweight += sensorMap.get(rcl.edgelet.sensorType);
            }
        }

        int m1 = Math.max(pesInUse, currentCPUs);
        capacity /= Math.max(m1, sensorweight);

        return capacity;
    }

    /**
     * Cancels execution of a cloudlet.
     *
     * @param cloudletId ID of the cloudlet being cancealed
     * @return the canceled cloudlet, $null if not found
     * @pre $none
     * @post $none
     */
    @Override
    public Cloudlet cloudletCancel(int cloudletId) {
        boolean found = false;
        int position = 0;

        // First, looks in the finished queue
        found = false;
        for (ResEdgeLet rcl : getCloudletFinishedList()) {
            if (rcl.getCloudletId() == cloudletId) {
                found = true;
                break;
            }
            position++;
        }

        if (found) {
            return getCloudletFinishedList().remove(position).getCloudlet();
        }

        // Then searches in the exec list
        position=0;
        for (ResEdgeLet rcl : getCloudletExecList()) {
            if (rcl.getCloudletId() == cloudletId) {
                found = true;
                break;
            }
            position++;
        }

        if (found) {
            ResEdgeLet rcl = getCloudletExecList().remove(position);
            if (rcl.getRemainingCloudletLength() == 0) {
                cloudletFinish(rcl);
            } else {
                rcl.setCloudletStatus(Cloudlet.CANCELED);
            }
            return rcl.getCloudlet();
        }

        // Now, looks in the paused queue
        found = false;
        position=0;
        for (ResEdgeLet rcl : getCloudletPausedList()) {
            if (rcl.getCloudletId() == cloudletId) {
                found = true;
                rcl.setCloudletStatus(Cloudlet.CANCELED);
                break;
            }
            position++;
        }

        if (found) {
            return getCloudletPausedList().remove(position).getCloudlet();
        }

        return null;
    }

    /**
     * Pauses execution of a cloudlet.
     *
     * @param cloudletId ID of the cloudlet being paused
     * @return $true if cloudlet paused, $false otherwise
     * @pre $none
     * @post $none
     */
    @Override
    public boolean cloudletPause(int cloudletId) {
        boolean found = false;
        int position = 0;

        for (ResEdgeLet rcl : getCloudletExecList()) {
            if (rcl.getCloudletId() == cloudletId) {
                found = true;
                break;
            }
            position++;
        }

        if (found) {
            // remove cloudlet from the exec list and put it in the paused list
            ResEdgeLet rcl = getCloudletExecList().remove(position);
            if (rcl.getRemainingCloudletLength() == 0) {
                cloudletFinish(rcl);
            } else {
                rcl.setCloudletStatus(Cloudlet.PAUSED);
                getCloudletPausedList().add(rcl);
            }
            return true;
        }
        return false;
    }


    //MARK
    /**
     * Processes a finished cloudlet.
     *
     * @param rcl finished cloudlet
     * @pre rgl != $null
     * @post $none
     */
    @Override
    public void cloudletFinish(ResCloudlet rcl) {
        rcl.setCloudletStatus(Cloudlet.SUCCESS);
        rcl.finalizeCloudlet();
        getCloudletdummyFinishedList().add(rcl);
    }

    public void cloudletFinish(ResEdgeLet rcl) {
        rcl.setCloudletStatus(Cloudlet.SUCCESS);
        rcl.finalizeCloudlet();
        getCloudletFinishedList().add(rcl);
    }

    /**
     * Resumes execution of a paused cloudlet.
     *
     * @param cloudletId ID of the cloudlet being resumed
     * @return expected finish time of the cloudlet, 0.0 if queued
     * @pre $none
     * @post $none
     */
    @Override
    public double cloudletResume(int cloudletId) {
        boolean found = false;
        int position = 0;

        // look for the cloudlet in the paused list
        for (ResEdgeLet rcl : getCloudletPausedList()) {
            if (rcl.getCloudletId() == cloudletId) {
                found = true;
                break;
            }
            position++;
        }

        if (found) {
            ResEdgeLet rgl = getCloudletPausedList().remove(position);
            rgl.setCloudletStatus(Cloudlet.INEXEC);
            getCloudletExecList().add(rgl);

            // calculate the expected time for cloudlet completion
            // first: how many PEs do we have?

            double remainingLength = rgl.getRemainingCloudletLength();
            double estimatedFinishTime = CloudSim.clock()
                    + (remainingLength / (getCapacity(getCurrentMipsShare()) * rgl.getNumberOfPes()));

            return estimatedFinishTime;
        }

        return 0.0;
    }

    /**
     * Receives an cloudlet to be executed in the VM managed by this scheduler.
     *
     * @param cloudlet the submited cloudlet
     * @param fileTransferTime time required to move the required files from the SAN to the VM
     * @return expected finish time of this cloudlet
     * @pre gl != null
     * @post $none
     */
    public double cloudletSubmit(EdgeLet cloudlet, double fileTransferTime) {
        ResEdgeLet rcl = new ResEdgeLet(cloudlet);
        rcl.setCloudletStatus(Cloudlet.INEXEC);
        for (int i = 0; i < cloudlet.getNumberOfPes(); i++) {
            rcl.setMachineAndPeId(0, i);
        }

        getCloudletExecList().add(rcl);

        // use the current capacity to estimate the extra amount of
        // time to file transferring. It must be added to the cloudlet length
        double extraSize = getCapacity(getCurrentMipsShare()) * fileTransferTime;
        long length = (long) (cloudlet.getCloudletLength() + extraSize);
        cloudlet.setCloudletLength(length);

        return cloudlet.getCloudletLength() / getCapacity(getCurrentMipsShare());
    }

    public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
        ResCloudlet rcl = new ResCloudlet(cloudlet);
        rcl.setCloudletStatus(Cloudlet.INEXEC);
        for (int i = 0; i < cloudlet.getNumberOfPes(); i++) {
            rcl.setMachineAndPeId(0, i);
        }

        getCloudletdummyExecList().add(rcl);

        // use the current capacity to estimate the extra amount of
        // time to file transferring. It must be added to the cloudlet length
        double extraSize = getCapacity(getCurrentMipsShare()) * fileTransferTime;
        long length = (long) (cloudlet.getCloudletLength() + extraSize);
        cloudlet.setCloudletLength(length);

        return cloudlet.getCloudletLength() / getCapacity(getCurrentMipsShare());
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.CloudletScheduler#cloudletSubmit(cloudsim.Cloudlet)
     */
    @Override
    public double cloudletSubmit(Cloudlet cloudlet) {
        return cloudletSubmit(cloudlet, 0.0);
    }

    /**
     * Gets the status of a cloudlet.
     *
     * @param cloudletId ID of the cloudlet
     * @return status of the cloudlet, -1 if cloudlet not found
     * @pre $none
     * @post $none
     */
    @Override
    public int getCloudletStatus(int cloudletId) {
        for (ResEdgeLet rcl : getCloudletExecList()) {
            if (rcl.getCloudletId() == cloudletId) {
                return rcl.getCloudletStatus();
            }
        }
        for (ResEdgeLet rcl : getCloudletPausedList()) {
            if (rcl.getCloudletId() == cloudletId) {
                return rcl.getCloudletStatus();
            }
        }
        return -1;
    }

    /**
     * Get utilization created by all cloudlets.
     *
     * @param time the time
     * @return total utilization
     */
    @Override
    public double getTotalUtilizationOfCpu(double time) {
        double totalUtilization = 0;
        for (ResEdgeLet gl : getCloudletExecList()) {
            totalUtilization += gl.getCloudlet().getUtilizationOfCpu(time);
        }
        return totalUtilization;
    }

    /**
     * Informs about completion of some cloudlet in the VM managed by this scheduler.
     *
     * @return $true if there is at least one finished cloudlet; $false otherwise
     * @pre $none
     * @post $none
     */
    @Override
    public boolean isFinishedCloudlets() {
        return getCloudletFinishedList().size() > 0;
    }

    /**
     * Returns the next cloudlet in the finished list, $null if this list is empty.
     *
     * @return a finished cloudlet
     * @pre $none
     * @post $none
     */
    @Override
    public Cloudlet getNextFinishedCloudlet() {
        if (getCloudletFinishedList().size() > 0) {
            return getCloudletFinishedList().remove(0).getCloudlet();
        }
        return null;
    }

    /**
     * Returns the number of cloudlets runnning in the virtual machine.
     *
     * @return number of cloudlets runnning
     * @pre $none
     * @post $none
     */
    @Override
    public int runningCloudlets() {
        return getCloudletExecList().size();
    }

    /**
     * Returns one cloudlet to migrate to another vm.
     *
     * @return one running cloudlet
     * @pre $none
     * @post $none
     */
    @Override
    public Cloudlet migrateCloudlet() {
        ResEdgeLet rgl = getCloudletExecList().remove(0);
        rgl.finalizeCloudlet();
        return rgl.getCloudlet();
    }

    /**
     * Gets the cloudlet exec list.
     *
     * @param <T> the generic type
     * @return the cloudlet exec list
     */
    @SuppressWarnings("unchecked")
    protected <T extends ResEdgeLet> List<T> getCloudletExecList() {
        return (List<T>) cloudletExecList;
    }

    @SuppressWarnings("unchecked")
    private <E> List<E> getCloudletdummyExecList() {
        return (List<E>) dummyList;
    }

    /**
     * Sets the cloudlet exec list.
     *
     * @param <T> the generic type
     * @param cloudletExecList the new cloudlet exec list
     */
    protected <T extends ResEdgeLet> void setCloudletExecList(List<T> cloudletExecList) {
        this.cloudletExecList = cloudletExecList;
    }
    /**
     * Gets the cloudlet paused list.
     *
     * @param <T> the generic type
     * @return the cloudlet paused list
     */
    @SuppressWarnings("unchecked")
    protected <T extends ResEdgeLet> List<T> getCloudletPausedList() {
        return (List<T>) cloudletPausedList;
    }

    /**
     * Sets the cloudlet paused list.
     *
     * @param <T> the generic type
     * @param cloudletPausedList the new cloudlet paused list
     */
    protected <T extends ResEdgeLet> void setCloudletPausedList(List<T> cloudletPausedList) {
        this.cloudletPausedList = cloudletPausedList;
    }
    /**
     * Gets the cloudlet finished list.
     *
     * @param <T> the generic type
     * @return the cloudlet finished list
     */
    @SuppressWarnings("unchecked")
    protected <T extends ResEdgeLet> List<T> getCloudletFinishedList() {
        return (List<T>) cloudletFinishedList;
    }

    @SuppressWarnings("unchecked")
    private <E> List getCloudletdummyFinishedList() {
        return (List<E>) dummyList;
    }

    /**
     * Sets the cloudlet finished list.
     *
     * @param <T> the generic type
     * @param cloudletFinishedList the new cloudlet finished list
     */
    protected <T extends ResEdgeLet> void setCloudletFinishedList(List<T> cloudletFinishedList) {
        this.cloudletFinishedList = cloudletFinishedList;
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.CloudletScheduler#getCurrentRequestedMips()
     */
    @Override
    public List<Double> getCurrentRequestedMips() {
        List<Double> mipsShare = new ArrayList<Double>();
        return mipsShare;
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.CloudletScheduler#getTotalCurrentAvailableMipsForCloudlet(cloudsim.ResCloudlet,
     * java.util.List)
     */
    @Override
    public double getTotalCurrentAvailableMipsForCloudlet(ResCloudlet rcl, List<Double> mipsShare) {
        return getCapacity(getCurrentMipsShare());
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.CloudletScheduler#getTotalCurrentAllocatedMipsForCloudlet(cloudsim.ResCloudlet,
     * double)
     */
    @Override
    public double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl, double time) {
        return 0.0;
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.CloudletScheduler#getTotalCurrentRequestedMipsForCloudlet(cloudsim.ResCloudlet,
     * double)
     */
    @Override
    public double getTotalCurrentRequestedMipsForCloudlet(ResCloudlet rcl, double time) {
        // TODO Auto-generated method stub
        return 0.0;
    }

    @Override
    public double getCurrentRequestedUtilizationOfRam() {
        double ram = 0;
        for (ResEdgeLet cloudlet : cloudletExecList) {
            ram += cloudlet.getCloudlet().getUtilizationOfRam(CloudSim.clock());
        }
        return ram;
    }

    @Override
    public double getCurrentRequestedUtilizationOfBw() {
        double bw = 0;
        for (ResEdgeLet cloudlet : cloudletExecList) {
            bw += cloudlet.getCloudlet().getUtilizationOfBw(CloudSim.clock());
        }
        return bw;
    }

}
