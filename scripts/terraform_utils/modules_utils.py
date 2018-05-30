import json
import os
import hcl
import sh


def get_terraform_modules(terraform_module_file):
    with open(terraform_module_file, 'r') as fp:
        modules_tf = hcl.load(fp)
        return modules_tf['module'].keys()


def get_module_paths(terraform_module_file):
    with open(terraform_module_file, 'r') as fp:
        modules_tf = hcl.load(fp)
        return [source['source'].replace("./", "") for source in modules_tf["module"].values()]


def get_module_dirs(terraform_module_file):
    with open(terraform_module_file, 'r') as fp:
        modules_tf = json.load(fp)
        return [module['Dir'].replace("./", "") for module in modules_tf['Modules']]


def get_directories_in_path(path):
    dirs = []
    for dir_name in os.listdir(path):
        abs_dir_path = os.path.join(path, dir_name)
        if os.path.isdir(abs_dir_path):
            dirs.append(abs_dir_path)
    return dirs


def get_service_paths(source_path):
    directories = get_directories_in_path(source_path)
    services = dict()
    for directory in directories:
        name = get_services_json_project_name(directory)
        if name is not None:
            services[name] = directory
    return services


def get_services_json_project_name(path):
    services_info_path = "{}/services-info.json".format(path)
    if os.path.isfile(services_info_path):
        with open(services_info_path, 'r') as sj:
            service = json.load(sj)
            if "project" in service and "name" in service["project"]:
                return service["project"]["name"]
    return None


def get_module_source_paths(modules, source_path):
    services = get_service_paths(source_path)
    paths = dict()
    for module in modules:
        if module not in services:
            print "Error: Module {} not found in path: {}".format(module, source_path)
            continue
        directory = services[module]
        if has_terraform_dir(directory):
            paths[module] = directory
        else:
            exit("terraform/ not found in {}".format(directory))
    return paths


def get_module_source_terraform_paths(modules, source_path):
    module_paths = get_module_source_paths(modules, source_path)
    for module, path in module_paths.iteritems():
        module_paths[module] = "{}/terraform".format(path)
    return module_paths


def has_terraform_dir(path):
    return True if os.path.isdir("{}/terraform".format(path)) else False


def has_module_file(module_dir):
    module_file = "{}/modules.tf".format(module_dir)
    try:
        sh.ls(module_file)
    except sh.ErrorReturnCode as e:
        print("{} does not exist!".format(module_file))
        raise e


def is_git_repo(module_dir):
    try:
        sh.git("-C", module_dir, "status")
    except sh.ErrorReturnCode as e:
        print("{} is not a git repository".format(module_dir))
        raise e


def get_latest_commit(repo_path):
    try:
        return sh.git("-C", repo_path, "rev-parse", "HEAD").rstrip()
    except sh.ErrorReturnCode as e:
        print("Error getting commit for {}".format(repo_path))
        raise e


def git_tag(repo_path, tag_name, version, message=""):
    is_git_repo(repo_path)
    tag = "{}_{}".format(tag_name, version)
    try:
        sh.git("-C", repo_path, "tag", "-a", tag, "-f", "-m", message)
        sh.git("-C", repo_path, "push", "-f", "origin", tag)
    except sh.ErrorReturnCode as e:
        print("Error tagging git repo")
        raise e


def git_checkout_tag(repo_path, tag_name):
    is_git_repo(repo_path)
    try:
        sh.git("-C", repo_path, "fetch", "--tags")
        sh.git("-C", repo_path, "checkout", tag_name)
    except sh.ErrorReturnCode as e:
        print("Error tagging git repo")
        raise e
