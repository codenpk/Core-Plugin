package org.pdtextensions.repos.internal.wrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.pdtextensions.repos.FindResult;
import org.pdtextensions.repos.PEXReposPlugin;
import org.pdtextensions.repos.api.IFindResult;
import org.pdtextensions.repos.api.IModule;
import org.pdtextensions.repos.api.IModuleVersion;
import org.pdtextensions.repos.api.IRepositoryProvider;
import org.pdtextensions.repos.api.IRepositoryProviderFactory;
import org.pdtextensions.repos.internal.debug.DebugProvider;
import org.pdtextensions.repos.internal.storage.Provider;
import org.pdtextensions.repos.internal.storage.RepositoryStorage;

/**
 * The global repository provider wrapping access to all other repositories.
 * 
 * @author mepeisen
 */
public class GlobalRepositoryProvider implements IRepositoryProvider {
	
	/**
	 * The global repository provider type.
	 */
	public static final String TYPE = "GLOBAL";
	
	private List<IRepositoryProvider> providers = new ArrayList<IRepositoryProvider>();
	
	public GlobalRepositoryProvider() {
		if (PEXReposPlugin.debug()) {
			this.providers.add(new DebugProvider());
		}
		
		for (final Provider provider : RepositoryStorage.instance().providers()) {
			for (final IRepositoryProviderFactory factory : PEXReposPlugin.getFactories()) {
				if (factory.getType().equals(provider.getType())) {
					this.providers.add(factory.recoverPersistent(provider.getUri(), provider.getId()));
					break;
				}
			}
		}
	}

	@Override
	public Iterable<IModule> listModules() throws CoreException {
		final List<IModule> modules = new ArrayList<IModule>();
		for (final IRepositoryProvider provider : this.providers) {
			for (final IModule module : provider.listModules()) {
				modules.add(module);
			}
		}
		return modules;
	}

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public String getId() {
		return TYPE;
	}

	public Iterable<IRepositoryProvider> getProviders() {
		return Collections.unmodifiableList(this.providers);
	}

	public void registerProvider(IRepositoryProvider provider) {
		if (TYPE.equals(provider.getType())) {
			// skip this hack
			throw new IllegalArgumentException("Cannot register global provider");
		}
		if ("DEBUG".equals(provider.getType())) {
			// skip this hack
			throw new IllegalArgumentException("Cannot register debug provider");
		}
		if (!provider.getId().startsWith("temp-")) {
			// skip this hack
			throw new IllegalArgumentException("You need to register temporary providers");
		}
		
		final String id = RepositoryStorage.instance().getNextId();
		for (final IRepositoryProviderFactory factory : PEXReposPlugin.getFactories()) {
			if (factory.getType().equals(provider.getType())) {
				factory.persist(provider, id);
				break;
			}
		}
		
		if (!provider.getId().equals(id)) {
			// TODO Log
			return;
		}
		
		RepositoryStorage.instance().addProvider(id, provider.getType(), provider.getUri());
	}

	public void unregisterProvider(IRepositoryProvider provider) {
		RepositoryStorage.instance().removeProvider(provider.getId());
	}

	@Override
	public IFindResult findModule(String vendor, String name, String version) {
		final List<IModuleVersion> versions = new ArrayList<IModuleVersion>();
		for (final IRepositoryProvider provider : this.providers) {
			final IFindResult result = provider.findModule(vendor, name, version);
			if (!result.isOk()) {
				return result;
			}
			for (final IModuleVersion v : result.moduleVersions()) {
				versions.add(v);
			}
		}
		return new FindResult(Status.OK_STATUS, versions);
	}

	@Override
	public String getUri() {
		return TYPE;
	}

}
