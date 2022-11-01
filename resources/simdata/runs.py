import subprocess
import os
import shutil

simstands_location = r"C:\Users\User\standsim\standsimulator.exe"

runs_dir = os.path.join("//vmware-host/Shared Folders/runs")

runs = [dirname for dirname in os.listdir(runs_dir) if
        os.path.isdir(os.path.join(runs_dir, dirname)) and not dirname.startswith(".")]

for run in runs:
    print(run + ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    cwd = os.path.join(runs_dir, run)
    simexe = os.path.join(cwd, "standsimulator.exe")
    shutil.copyfile(simstands_location, simexe)
    p = subprocess.Popen(simexe, cwd=cwd, stdin=subprocess.PIPE)
    p.communicate(b"\n")
    return_code = p.wait()
    print(return_code)
    os.remove(simexe)

# current_dir = r"C:\Users\User\AppData\Windows\Start Menu\Programs"
# p = subprocess.Popen(os.path.join(current_dir,"file.exe"),cwd=current_dir)
# return_code = p.wait()
