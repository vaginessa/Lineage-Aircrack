# Aircrack on Sailfish Lineage
The goal of this project was to modify LineageOS 16.1 to support the [aircrack-ng](https://aircrack-ng.org/) wireless security testing suite on a Google Pixel (codename sailfish). The Google Pixel does not have a wireless card capable of packet injection, so this README has a guide on including kernel modules that allow a supported, external USB wireless card. If your goal is simply to install the aircrack suite, a special add-on package is included that will copy [precompiled](https://github.com/kriswebdev/android_aircrack) aircrack binaries to /system/xbin. If you would like a control app, [Hijacker](https://github.com/chrisk44/Hijacker) has been forked to interface with this implementation. 

This guide uses sailfish as the target device, but adaptation of this method to other devices supported by Lineage should be relatively easy. 

### By setting up this project, your phone will be *less* secure. This should only be used for educational purposes. 
#### This project is very technical as I have not had the time to implement a simple installer. Many features of Hijacker do not work. 
I explain how to pull source and build everything. Experience compiling a kernel and android apps is not required, but will help. 

## Setting up the drivers
This guide assumes the target device is a Google Pixel (Sailfish). If you would like to build for another device, start [here](https://wiki.lineageos.org/devices/). Look for your device, and follow the corresponding build guide.

1. Follow [this guide](https://wiki.lineageos.org/devices/sailfish/build) to set up the build tools and source tree for LineageOS 16.1 on Sailfish. **Stop** after extracting proprietary blobs. It is assumed the source tree root is at Lineage/
2. Generate the kernel menuconfig

    a. Change directories to the kernel root. 
  ```bash
  cd Lineage/kernel/google/marlin
  ```
  The Marlin codename is for the Google Pixel XL, which has rougly the same kernel as the Sailfish. For another device, this directory will likely be `Lineage/kernel/<manufacturer>/<codename>`
  
    b. Generate the kernel configuration file.
    make ARCH=arm64 lineageos_marlin_defconfig SELINUX_DEFCONFIG=selinux_defconfig

## Installing the standalone binaries


## Hijacker

Hijacker is a Graphical User Interface for the penetration testing tools [Aircrack-ng, and Airodump-ng](https://www.aircrack-ng.org/). It offers a simple and easy UI to use these tools without typing commands in a console and copy&pasting MAC addresses.

This fork changed extensive things. 

