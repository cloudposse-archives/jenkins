#!/usr/bin/env python
import subprocess
import multiprocessing
import logging
from terraform_utils.modules_utils import *


def zip_tf_modules():
    logging.info('[ZIPPING] Started')

    module_file = '{}/.terraform/modules/modules.json'.format(os.getcwd())
    if not os.path.exists(module_file):
        logging.info("[ZIPPING] No modules.tf file - {} - skipping zip".format(module_file))
        return

    # for each module call zip-components with output going to terraform/lambda_files
    modules = get_module_dirs(module_file)
    zip_procs = [["zip-components.py", "-c", module_source_path, "-z", '{}/lambda_files'.format(module_source_path)] for
                 module_source_path in modules]

    for process in zip_procs:
        print process

    pool = multiprocessing.Pool()
    pool.map(subprocess.check_output, zip_procs)
    pool.close()
    pool.join()
    logging.info('[ZIPPING] Complete')


def main():
    zip_tf_modules()


if __name__ == "__main__":
    main()
